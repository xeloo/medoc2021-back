package org.xsite.webapp;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.xsite.server.Context;
import org.xsite.server.HttpMethod;

import ru.xeloo.json.JSON;

import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

public abstract class RestModule extends AppModule {

    private static final String JSON_CONTENT_TYPE = "application/json";

    private static final Map<Class<? extends Annotation>, HttpMethod> METHODS = Map.of(
        ANY.class, HttpMethod.ANY,
        GET.class, HttpMethod.GET,
        POST.class, HttpMethod.POST,
        PUT.class, HttpMethod.PUT,
        DELETE.class, HttpMethod.DELETE
    );

    private final String basePath;
    private String cors;

    public RestModule(String basePath) {
        if( basePath.endsWith("/") )
            basePath = basePath.substring(0, basePath.length() - 1);
        this.basePath = basePath;

        for( Method m : getClass().getDeclaredMethods() ) {
            int mods = m.getModifiers();
            if( isPublic(mods) && !isStatic(mods) ) {
                m.setAccessible(true);

                Annotation[] annotations = m.getDeclaredAnnotations();
                if( annotations.length == 0 ) {
                    addHandler(HttpMethod.ANY, m, "");
                }
                for( Annotation a : annotations ) {
                    HttpMethod method = METHODS.get(a.annotationType());
                    if( method != null ) {
                        try {
                            String path = (String)a.getClass().getMethod("value").invoke(a);
                            addHandler(method, m, path);
                        } catch( Exception e ) {
                            System.err.println("[UNEXPECTED] " + e.toString());
                        }
                    }
                }
            }
        }

    }

    public void cors(String cors) {
        this.cors = cors;

        try {
            Method corsMethod = RestModule.class.getDeclaredMethod("corsOptionsMethod");
            addHandler(HttpMethod.OPTIONS, null, "/*");
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    private void corsOptionsMethod() {
        System.out.println("CORS-OPTIONS");
    }

    private void addHandler(HttpMethod httpMethod, Method executable, String path) {
        if( path == null || path.isEmpty() ) {
            path = "/" + executable.getName();
        } else if( path.equals("/") ) {
            path = "";
        } else if( !path.startsWith("/") ) {
            path = "/" + path;
        }

        webApp.request(httpMethod, basePath + path, ctx -> {
            if( cors != null ) {
                String origin = ctx.header("Origin");
                // TODO: check origin
                ctx.header("Access-Control-Allow-Origin", origin);
                ctx.header("Access-Control-Allow-Credentials", "true");
                if( httpMethod == HttpMethod.OPTIONS ) {
                    ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    ctx.header("Access-Control-Allow-Headers", "Content-Type");
                    ctx.header("Access-Control-Max-Age", "86400");
                    return;
                }
            }

            try {
                Object r = executable.invoke(this);
                if( !ctx.completed() ) {
                    ajaxResult(r);
                }
            } catch( IllegalAccessException e ) {
                e.printStackTrace();
            } catch( InvocationTargetException e ) {
                Throwable tex = e.getTargetException();
                if( tex instanceof AjaxResponse ) {
                    if( !ctx.completed() ) {
                        ctx.status(400).contentType(JSON_CONTENT_TYPE).send(JSON.stringify(tex));
                    }
                    return;
                }
                String message = tex.getMessage();
                if( !ctx.completed() ) {
                    json(500, new AjaxResponse(1, message));
                }
                RuntimeException rex = new RuntimeException(tex.getClass().getName() + (message == null ? "" : ": " + message), tex);
                rex.setStackTrace(tex.getStackTrace());
                throw rex;
            }
        });
    }

    protected HttpMethod method() {
        return Context.current().method;
    }

    protected String body() throws IOException {
        return Context.current().body();
    }

    protected String jsonBody() throws IOException {
        Context ctx = Context.current();
        if( !JSON_CONTENT_TYPE.equals(ctx.contentType) ) {
            throw new IOException("Invalid content type, expected " + JSON_CONTENT_TYPE);
        }
        return ctx.body();
    }


    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    protected @interface ANY {
        String value() default  "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    protected @interface GET {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    protected @interface POST {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    protected @interface PUT {
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    protected @interface DELETE {
        String value() default "";
    }


}
