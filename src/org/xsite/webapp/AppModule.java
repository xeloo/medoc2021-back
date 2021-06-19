package org.xsite.webapp;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xsite.server.Context;
import org.xsite.server.HttpMethod;
import org.xsite.webapp.params.Param;

import ru.xeloo.json.JSON;

public abstract class AppModule {

    private static final String VALIDATION_ERRORS = "_validation_errors_";
    protected WebApp webApp;

    private String wsPath;
    private String ajaxPath;
    private Map<String, RequestHandler> apiMethods;

    protected RequestHandler STATIC(String path) {
        return ctx -> ctx.send(Path.of(path));
    }

    public AppModule() {
//        System.out.println("Module: " + this.getClass().getName());
        webApp = WebApp.current();
    }

    public void get(RequestHandler handler) {
        webApp.request(HttpMethod.GET, "/*", handler);
    }

    public void get(String path, RequestHandler handler) {
        webApp.request(HttpMethod.GET, path, handler);
    }

    public void post(RequestHandler handler) {
        webApp.request(HttpMethod.POST, "/*", handler);
    }

    public void post(String path, RequestHandler handler) {
        webApp.request(HttpMethod.POST, path, handler);
    }

    public void any(String path, RequestHandler handler) {
        webApp.request(HttpMethod.ANY, path, handler);
    }

    protected void ajaxPath(String path) {
        if( ajaxPath != null ) {
            System.err.println("ALERT : AJAX already initialized for module: " + getClass().getName());
        }

        if( apiMethods == null ) {
            apiMethods = new HashMap<>();
        }

        if( !path.endsWith("/") ) {
            path += "/";
        }
        ajaxPath = path + ":method";

        webApp.request(HttpMethod.ANY, ajaxPath, ctx -> {
            String method = param("method").getString();
            RequestHandler handler = apiMethods.get(method);
            if( handler != null ) {
                handler.handle(ctx);
            }
        });
    }

    protected void wsPath(String path) {
        if( wsPath != null ) {
            System.err.println("ALERT : WebSocket already initialized for module: " + getClass().getName());
        }

        if( apiMethods == null ) {
            apiMethods = new HashMap<>();
        }

        if( !path.endsWith("/") ) {
            path += "/";
        }
        wsPath = path + ":method";

/*
        webApp.ws(wsPath, ctx -> {
            String method = param("method").getString();
            WebApp.RequestHandler handler = apiMethods.get(method);
            if( handler != null ) {
                handler.handle(ctx);
            }
        });
*/
    }

    private void initAjax() {
    }

    public void api(String method, RequestHandler handler) {
        initAjax();
        if( apiMethods.put(method, handler) != null ) {
            System.err.println("ALERT : Duplicate AJAX method: " + method);
        }
    }

    private String _path;
    public void path(String path, Runnable block) {
        if( _path != null ) {
            throw new RuntimeException("Nested path method not allowed (TODO)");
        }
        _path = path;
        block.run();
        _path = null;
    }

    public void method(String method, WebApp.ApiMethod handler, WebApp.AccessController... controllers) {
        webApp.method(_path, method, handler, controllers);
    }


    @Deprecated
    public void ajax(String method) {
    }

    @Deprecated
    protected void ajax(String method, Runnable handler) {
//        AjaxModule.register(method, handler);
    }

    protected final void render(String templatePath) {
        Context context = Context.current();
//        context.render(templatePath, context.get(ViewModel.class));
    }



    protected void ajaxResult(Object data) {
        json(Context.current(), new AjaxResponse(0, data));
    }

    protected void ajaxResult(String message) {
        json(Context.current(), new AjaxResponse(0, message));
    }

    protected void ajaxResult(int status, String message) {
        json(Context.current(), new AjaxResponse(status, message));
    }

    protected void ajaxResult(int status, Object data) {
        json(Context.current(), new AjaxResponse(status, data));
    }

    protected void ajaxError(int status, String message) {
        ajaxError(Context.current(), status, message);
    }

    protected boolean sendFormError() {
        if( hasErrors() ) {
            throw new AjaxResponse(2, validationErrors(), "Incorrect parameters");
        }
        return false;
    }

    protected void ajaxError(Context ctx, int status, String message) {
        json(ctx, 400, new AjaxResponse(status, message));
    }

    protected void json(Object data) {
        json(Context.current(), data);
    }
    protected void json(int status, Object data) {
        json(Context.current(), status, data);
    }
    protected void json(Context ctx, Object data) {
        json(ctx, JSON.stringify(data));
    }
    protected void json(Context ctx, int status, Object data) {
        json(ctx, status, JSON.stringify(data));
    }
    protected void json(String json) {
        json(Context.current(), json);
    }
    protected void json(int status, String json) {
        json(Context.current(), status, json);
    }
    protected void json(Context ctx, String json) {
        ctx.contentType("application/json").send(json);
    }
    protected void json(Context ctx, int status, String json) {
        ctx.status(status).contentType("application/json").send(json);
    }




    public static class AjaxResponse extends RuntimeException {
        public Integer requestId;
        public int status;
        public Object data;
        public String message;
        public String redirect;

        public AjaxResponse() {
        }

        public AjaxResponse(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public AjaxResponse(int status, Object data) {
            this.status = status;
            this.data = data;
        }

        public AjaxResponse(int status, Object data, String message) {
            this.status = status;
            this.data = data;
            this.message = message;
        }

        public AjaxResponse redirect(String url) {
            redirect = url;
            return this;
        }

    }


    public Param param(String name, Param.Rule... rules) {
        Context ctx = Context.current();
        WebApp.ApiMessage msg = ctx.get(WebApp.ApiMessage.class);
        Object value;
        if( msg == null ) {
            value = ctx.param(name);
            if( value == null )
                value = ctx.file(name);
        } else {
            value = msg.params != null ? msg.params.get(name) : null;
        }
        Param param = new Param(name, value);
        if( rules.length != 0 )
            param.check(rules);
        return param;
    }

    public <T> T param(Class<T> type) {
        return null;
    }

    public static Map<String, List<String>> validationErrors() {
        Context context = Context.current();
        Map<String, List<String>> map = context.get(VALIDATION_ERRORS);
        if( map == null ) {
            context.set(VALIDATION_ERRORS, map = new HashMap<>());
        }
        return map;
    }

    public static void resetValidationErrors() {
        Context.current().set(VALIDATION_ERRORS, null);
    }

    public boolean hasErrors() {
        Map<String, List<String>> map = Context.current().get(VALIDATION_ERRORS);
        return map != null && map.size() > 0;
    }

}
