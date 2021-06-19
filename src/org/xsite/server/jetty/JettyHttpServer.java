package org.xsite.server.jetty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.LowResourceMonitor;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.xsite.server.Context;
import org.xsite.server.HttpServer;
import org.xsite.server.HttpServerImpl;

import static org.eclipse.jetty.servlet.ServletContextHandler.SESSIONS;

public class JettyHttpServer implements HttpServerImpl {

    private static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";

    private static final String CONTEXT_PATH = "/";

    private static final Object BASE_REQUEST = new Object();

    private Server jettyServer;

    private HttpServer appServer;

    public JettyHttpServer(HttpServer server) {
        this.appServer = server;
    }


    @Override
    public String getName() {
        return "Jetty " + Server.getVersion();
    }

    @Override
    public void start(int port, HttpServlet httpServlet) {
        disableJettyLogger();

        jettyServer = new Server(new QueuedThreadPool(250, 8, 60_000));
        jettyServer.addBean(new LowResourceMonitor(jettyServer));
        jettyServer.insertHandler(new StatisticsHandler());

/*
        resourceHandler = new ResourceHandler() {
            @Override
            public Resource getResource(String path) {
                Path resource = Context.current().attr(RESOURCE);
                if( resource != null )
                    return Resource.newResource(resource);
                return null;
            }
        };
*/

        SessionHandler sessionHandler = new SessionHandler();
        sessionHandler.setHttpOnly(true);

        GzipHandler gzipHandler = new GzipHandler();

        ServletContextHandler httpHandler = new ServletContextHandler(null, CONTEXT_PATH, SESSIONS) {
            @Override
            public void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                appServer.createContext(request, response, JettyHttpServer.this);

                if( request.getHeader(SEC_WEBSOCKET_KEY) != null )
                    return; // don't touch websocket requests

                try {
                    nextHandle(target, baseRequest, request, response);
                } finally {
                    appServer.destroyContext();
                }
            }
        };
        httpHandler.setSessionHandler(sessionHandler);
        httpHandler.setGzipHandler(gzipHandler);
        ServletHolder servlet = new ServletHolder(httpServlet);
        servlet.getRegistration().setMultipartConfig(new MultipartConfigElement(appServer.tempDir().toString(),-1, -1, 0));
        httpHandler.addServlet(servlet, "/*");

        ServletContextHandler webSocketHandler = new ServletContextHandler(null, CONTEXT_PATH, SESSIONS);
        webSocketHandler.addServlet(new ServletHolder(new JettyWsServlet()), "/*");

        HandlerWrapper defaultHandler = (HandlerWrapper)jettyServer.getServer().getHandler();
        defaultHandler.setHandler(new HandlerList(httpHandler, webSocketHandler));
//        HandlerWrapper handler = new HandlerWrapper();
//        handler.setHandler(new HandlerList(defaultHandler, httpHandler, webSocketHandler));
//        handler.setHandler(new HandlerList(webSocketHandler, httpHandler));
//        server.setHandler(handler);

        ServerConnector connector = new ServerConnector(jettyServer);
        connector.setPort(port);
        jettyServer.setConnectors(new Connector[]{connector});

        try {
            jettyServer.start();
        } catch( Exception e ) {
            throw new RuntimeException(e);
        }

        System.out.println("Listening on " +
                           (connector.getProtocols().contains("ssl") ? "https" : "http") + "://" +
                           (connector.getHost() != null ? connector.getHost() : "localhost") + ":" +
                           connector.getPort() + CONTEXT_PATH);

        enableJettyLogger();
    }

    @Override
    public void stop() {
        try {
            jettyServer.stop();
            jettyServer.destroy();
            jettyServer = null;
        } catch( Exception e ) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendStaticResource(Path path) {
        Context context = Context.current();
        HttpServletRequest request = context.req;
        HttpServletResponse response = context.res;
        try {
            int maxAge = path.startsWith("/immutable/") ? 31622400 : 0;
            response.setHeader("Cache-Control", "max-age=" + maxAge);
            // Remove the default content type because Jetty will not set the correct one
            // if the HTTP response already has a content type set
            response.setContentType(null);
/*
            if( target.endsWith(".html") ) {
                httpResponse.setContentType("text/html; charset=UTF-8");
                httpResponse.setCharacterEncoding("UTF-8");
            }
*/
//            resourceHandler.handle(path.toString(), context.attr(BASE_REQUEST), request, response);
            // TODO: check res handler headers

            response.setContentType(request.getServletContext().getMimeType(path.toString()));
            response.setContentLengthLong(Files.size(path));
            response.setCharacterEncoding("utf-8");
            Files.copy(path, response.getOutputStream());

        } catch( Exception e ) {
            System.err.println("Exception occurred while sending static resource: " + path);
            System.err.println(e.getCause().toString());
//            e.printStackTrace();
        }

    }


    private Logger jettyLogger;
    private final Logger noopLogger = new Logger() {
        @Override
        public String getName() {
            return "noop";
        }

        @Override
        public void warn(String msg, Object... args) {
        }

        @Override
        public void warn(Throwable thrown) {
        }

        @Override
        public void warn(String msg, Throwable thrown) {
        }

        @Override
        public void info(String msg, Object... args) {
        }

        @Override
        public void info(Throwable thrown) {
        }

        @Override
        public void info(String msg, Throwable thrown) {
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public void setDebugEnabled(boolean enabled) {
        }

        @Override
        public void debug(String msg, Object... args) {
        }

        @Override
        public void debug(String msg, long value) {
        }

        @Override
        public void debug(Throwable thrown) {
        }

        @Override
        public void debug(String msg, Throwable thrown) {
        }

        @Override
        public Logger getLogger(String name) {
            return this;
        }

        @Override
        public void ignore(Throwable ignored) {
        }
    };

    private void disableJettyLogger() {
        jettyLogger = Log.getLog();
        Log.setLog(noopLogger);
    }

    private void enableJettyLogger() {
        Log.setLog(jettyLogger);
    }

}
