package org.xsite.webapp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.xsite.server.Context;
import org.xsite.server.HttpMethod;
import org.xsite.server.ResponseException;
import org.xsite.server.ws.WsChannel;
import org.xsite.server.ws.WsContext;
import org.xsite.server.ws.WsHandler;

import ru.xeloo.json.JSON;

public abstract class WebApp {

    private static final Path ROOT = Path.of("/");
    protected static final String ASSETS_DIR = "assets";
    protected static final String UPLOAD_DIR = "upload";

    private final List<Path> baseDirs = new ArrayList<>();

    private final RequestManager requestManager = new RequestManager();

    private final Map<String, WsHandler> wsHandlers = new HashMap<>();

    /**
     * Used by AppModule on initialization
     */
    private static final ThreadLocal<WebApp> localWebApp = new ThreadLocal<>();

    private String apiPath;
    private final Map<String, Map<String, MethodDescriptor>> pathToMethods = new HashMap<>();
    private final Map<String, List<WsChannel>> pathToWsConnections = new ConcurrentHashMap<>();

    private final Map<Object, Supplier<?>> providerMap = new HashMap<>();
    private Path tempDir = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath();
    private Path uploadDir;


    public WebApp() {
        localWebApp.set(this);
        addProvider(ViewModel.class, ViewModel::new);
    }

    /**
     * Used by AppModule on initialization
     *
     * @return
     */
    static WebApp current() {
        return localWebApp.get();
    }

    public void addProvider(Object key, Supplier<?> provider) {
        providerMap.put(key, provider);
    }

    public <T> T createValue(Object key) {
        return (T)providerMap.getOrDefault(key, () -> null).get();
    }


    protected void addModule(Class<? extends AppModule> moduleClass) {
        try {
            moduleClass.getConstructor().newInstance();
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    public void request(HttpMethod method, String path, RequestHandler handler) {
        requestManager.add(method, path, handler);
    }

    public void apiPath(String path) {
        if( apiPath != null ) {
            throw new RuntimeException("Default API path already defined");
        }
        apiPath = path;
    }

    public void method(String path, String method, ApiMethod handler, AccessController... accessControllers) {
        if( path == null )
            path = apiPath;
        if( path == null )
            throw new RuntimeException("API path is not defined");

        if( !path.endsWith("/") )
            path += "/";

        Map<String, MethodDescriptor> map = pathToMethods.computeIfAbsent(path, p -> {
            request(HttpMethod.ANY, p + ":method", this::ajaxHandler);
            pathToWsConnections.put(p, new ArrayList<>());
            ws(p)
                .connect(ws -> {
                    pathToWsConnections.get(p).add(ws.channel);
                })
                .message(ws -> {
                    ApiMessage msg = ws.message(ApiMessage.class);
                    Context ctx = ws.context;
                    ctx.set(msg);
                    callMethod(ctx, ctx.path(), msg.method, msg.requestId, ws::send);
                })
                .binary(ws -> System.out.println("WS BINARY"))
                .close(ws -> {
                    pathToWsConnections.get(p).remove(ws.channel);
                })
                .error(ws -> {
                    System.err.println(ws.error);
                    pathToWsConnections.get(p).remove(ws.channel);
                });
            return new HashMap<>();
        });
        if( map.put(method, new MethodDescriptor(handler, accessControllers)) != null ) {
            System.out.println("Duplicate method declaration for '" + path + method + "'. Replaced.");
        }
    }

    public void sendEvent(String path, String event, Object data, AccessController... accessControllers) {
        try {
            if( !path.endsWith("/") ) {
                path += "/";
            }

            String message = formatEvent(event, data);

            List<WsChannel> channels = pathToWsConnections.get(path);
            if( channels != null ) {
                channels.forEach(channel -> {
                    if( accessControllers != null ) {
                        for( AccessController c : accessControllers ) {
                            if( !c.checkAccess(channel.context) ) {
                                return;
                            }
                        }
                    }

                    channel.send(message);
                });
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    private void ajaxHandler(Context ctx) {
        String method = ctx.param("method");
        String path = ctx.path;
        if( path.endsWith(method) ) {
            path = path.substring(0, path.length() - method.length());
        }
        callMethod(ctx, path, method, 0, result -> {
            ctx.contentType("application/json").send(result);
        });
    }

    /**
     * If complex app is used, returns actual web app to handle current request
     *
     * @param context
     * @return
     */
    public WebApp resolveWebApp(Context context) {
        return this;
    }

    public void setUploadDir(Path dir) {
        uploadDir = dir;
        addBaseDir(dir);
    }

    public Path uploadPath(Path dest) {
        if( uploadDir == null ) {
            if( baseDirs.isEmpty() ) {
                uploadDir = tempDir;
            } else {
                uploadDir = baseDirs.get(0).getParent().resolve(UPLOAD_DIR);
                addBaseDir(uploadDir);
            }
        }
        return uploadDir.resolve(ROOT.relativize(ROOT.resolve(dest).normalize()));
    }

    public void setTempDir(Path dir) {
        tempDir = dir;
    }

    public Path tempDir() {
        return tempDir;
    }

    interface ResponseSender {
        void send(String result);
    }

    private void callMethod(Context _ctx, String path, String method, int requestId, ResponseSender sender) {
        String p = path;
        if( !p.endsWith("/") )
            p += "/";
        MethodDescriptor descriptor = pathToMethods.getOrDefault(p, Collections.emptyMap()).get(method);
        if( descriptor != null ) {
            if( descriptor.controllers != null ) {
                for( AccessController c : descriptor.controllers ) {
                    if( !c.checkAccess(_ctx) ) {
                        AppModule.AjaxResponse r = new AppModule.AjaxResponse(1, "Access denied.");
                        r.requestId = requestId;
                        sender.send(JSON.stringify(r));
                        return;
                    }
                }
            }

            AppModule.resetValidationErrors();

            try {
                Object result = descriptor.method.invoke();

                if( result instanceof Callable ) {
                    Callable<?> callable = (Callable<?>)result;
                    CompletableFuture.runAsync(() -> {
                        try {
                            _ctx.activate();
                            Object r = callable.call();
                            _ctx.deactivate();

                            sender.send(formatResult(r, requestId));
                        } catch( Exception e ) {
                            sender.send(formatException(e, path, method, requestId));
                        }
                    });
                } else {
                    sender.send(formatResult(result, requestId));
                }
            } catch( Exception e ) {
                if( e instanceof ApiError ) {
                    AppModule.AjaxResponse r = new AppModule.AjaxResponse(1, e.getMessage());
                    r.requestId = requestId;
                    sender.send(JSON.stringify(r));
                } else if( e instanceof ResponseException ) {
                    // do nothing
                } else if( e instanceof AppModule.AjaxResponse ) {
                    ((AppModule.AjaxResponse)e).requestId = requestId;
                    sender.send(JSON.stringify(e));
                } else {
                    e.printStackTrace();
                    sender.send(formatException(e, path, method, requestId));
                }
            }
        } else {
            String m = "API method is not defined: " + method;
            System.err.println(m);
            sender.send("{" + (requestId != 0 ? "\"requestId\":" + requestId + "," : "")
                        + "\"status\":1,\"error\":{\"type\":\"UndefinedMethod\",\"message\":\"" + m + "\"}}");
        }
    }

    private String formatResult(Object result) {
        return formatResult(result, 0);
    }

    private String formatResult(Object result, int requestId) {
        return "{" + (requestId != 0 ? "\"requestId\":" + requestId + "," : "")
               + "\"status\":0" + (result != null ? ",\"data\":" + JSON.stringify(result) : "") + "}";
    }

    private String formatEvent(String event, Object data) {
        return "{\"event\":\"" + event + "\"" + (data != null ? ",\"data\":" + JSON.stringify(data) : "") + "}";
    }

    private String formatException(Throwable ex, String path, String method) {
        return formatException(ex, path, method, 0);
    }

    private String formatException(Throwable ex, String path, String method, int requestId) {
        Throwable cause = ex.getCause();
        if( cause == null ) cause = ex;
        cause.printStackTrace();
        String message = cause.getMessage();
        if( message != null ) {
            message = message.replace('"', '\'');
        } else {
            StackTraceElement ste = cause.getStackTrace()[0];
            String fileName = ste.getFileName();
            message = cause.toString() + ": " + fileName.substring(0, fileName.lastIndexOf('.'))
                      + "." + ste.getMethodName() + ":" + ste.getLineNumber();
        }

        String where = path + "/" + method;
        StackTraceElement[] stackTrace = cause.getStackTrace();
//        String className = method.getDeclaringClass().getName();
        String className = "";
        for( int i = 0; i < stackTrace.length; i++ ) {
            StackTraceElement ste = stackTrace[i];
            if( className.equals(ste.getClassName()) ) {
                where += "(:" + ste.getLineNumber() + ")";
                if( i > 0 ) {
                    ste = stackTrace[i - 1];
                    String c = ste.getClassName();
                    where += "->" + c.substring(c.lastIndexOf('.') + 1) + "." + ste.getMethodName() + "(:" + ste.getLineNumber() + ")";
                }
                break;
            }
        }

        return "{" + (requestId != 0 ? "\"requestId\":" + requestId + "," : "")
               + "\"status\":1,\"error\":{\"type\":\"" + cause.getClass().getSimpleName()
               + "\",\"where\":\"" + where
               + "\",\"message\":\"" + message + "\"}}";
    }


    public static class ApiMessage {
        int requestId;
        String method;
        Map<String, Object> params;
        int status;
        Object data;
        ApiError error;

        WsContext ctx;

        public ApiMessage() {
        }

        public ApiMessage(int requestId, int status, Object data) {
            this.requestId = requestId;
            this.status = status;
            this.data = data;
        }

        public ApiMessage(int requestId, int status, ApiError error) {
            this.requestId = requestId;
            this.status = status;
            this.error = error;
        }
    }

    public static class ApiError extends RuntimeException {
        String type;
        String where;
        String message;

        public ApiError(String message) {
            super(message);
            this.message = message;
        }

        public ApiError(String type, String message) {
            super(message);
            this.type = type;
            this.message = message;
        }
    }


    public WsHandler ws(String path) {
        WsHandler handler = new WsHandler();
        if( wsHandlers.put(path, handler) != null ) {
            System.err.println("Duplicate WebSocket handler: " + path);
        }
        return handler;
    }

    public WsHandler findWsHandler(String path) {
        return wsHandlers.get(path);
    }


    public void handle(Context ctx) {
        if( ctx.method == HttpMethod.GET ) {
            Path resource = findResource(ctx.path);
            if( resource != null ) {
                ctx.send(resource);
                return;
            }
        }

        RequestHandler handler = requestManager.findHandler(ctx);
        if( handler != null ) {
            try {
                handler.handle(ctx);
            } catch( Exception e ) {
                Throwable t = e instanceof AppModule.AjaxResponse ? e : e.getCause();
                if( !ctx.completed() && t instanceof AppModule.AjaxResponse ) {
                    ctx.status(400).contentType("application/json").send(JSON.stringify(t));
                    return;
                }
                throw e;
            }
            return;
        }

        ctx.status(404);
    }

    public void addBaseDir(Path path) {
        baseDirs.add(path);
    }

    public Path findResource(String target) {
        if( target == null || !target.startsWith("/") ) {
            return null;
        }
        return findResource(Path.of(target));
    }

    public Path findResource(Path target) {
        Path path = ROOT.relativize(target.normalize());

        return findResource(path, true || target.endsWith("/"));
    }

    protected Path findResource(Path path, boolean resolveIndex) {
        for( Path baseDir : baseDirs ) {
            Path resource = findResource(baseDir, path, resolveIndex);
            if( resource != null )
                return resource;
        }
        return null;
    }

    protected Path findResource(Path baseDir, Path path, boolean resolveIndex) {
        Path resource = baseDir.resolve(path);
        if( Files.exists(resource) ) {
            if( Files.isRegularFile(resource) ) {
                return resource;
            }
            if( resolveIndex && Files.isDirectory(resource) ) {
                resource = resource.resolve("index.html");
                if( Files.isRegularFile(resource) ) {
                    return resource;
                }
            }
        }
        return null;
    }


    public static class MethodDescriptor {
        ApiMethod method;
        AccessController[] controllers;

        public MethodDescriptor(ApiMethod method, AccessController[] controllers) {
            this.method = method;
            this.controllers = controllers != null && controllers.length == 0 ? null : controllers;
        }
    }

    public interface ApiMethod {
        Object invoke() throws Exception;
    }

    public interface AccessController {
        boolean checkAccess(Context ctx);
    }

}
