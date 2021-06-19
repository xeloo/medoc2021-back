package org.xsite.server;

import java.nio.file.Path;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xsite.server.jetty.JettyHttpServer;
import org.xsite.webapp.WebApp;

public class HttpServer {

    public static final String SERVER_NAME = "XServer";

    private static final Logger log = Logger.getGlobal();

    private static final String JETTY_SERVER = "org.eclipse.jetty.server.Server";

    private HttpServerImpl server;
    private int port;
    private WebApp webApp;

    public HttpServer() {
        try {
            Class.forName(JETTY_SERVER);
            server = new JettyHttpServer(this);
            return;
        } catch( ClassNotFoundException ignored ) {
        }

        throw new RuntimeException("HTTP Server implementation is not found");
    }

    public HttpServer start() {
        long startupTimer = System.currentTimeMillis();
        log.info("Starting HTTP Server (" + server.getName() + ") ...");

        server.start(port, new MyHttpServlet(webApp));

        log.info("HTTP Server started in " + (System.currentTimeMillis() - startupTimer) + "ms");
        return this;
    }

    public void stop() {
        server.stop();
    }

    public HttpServer port(int port) {
        this.port = port;
        return this;
    }

    public HttpServer app(WebApp webApp) {
        this.webApp = webApp;
        return this;
    }

    public void createContext(HttpServletRequest request, HttpServletResponse response, HttpServerImpl server) {
        Context ctx = new Context(request, response, server, webApp);
        ctx.header("Server", SERVER_NAME);
//                ctx.contentType(defaultContentType);
        ctx.activate();
    }

    public void destroyContext() {
        Context.current().deactivate();
    }

    public Path tempDir() {
        return webApp.tempDir();
    }
}
