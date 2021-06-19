package org.xsite.server;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xsite.webapp.WebApp;

public class MyHttpServlet extends HttpServlet {

//    public String defaultContentType = "text/plain";
    public String defaultContentType = "text/html; charset=UTF-8";

    private final WebApp webApp;

    public MyHttpServlet(WebApp app) {
        webApp = app;
    }

    @Override
    public void init() {
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) {
        long executionTime = System.nanoTime();

        String requestUri = req.getRequestURI();

        // TODO: Define WebApp by host name
        // TODO: Set attr sessionId

        if( webApp != null ) {
            Context ctx = Context.current();
            try {
                webApp.handle(ctx);
            } catch( Exception e ) {
                if( !(e instanceof ResponseException) ) {
                    e.printStackTrace();
                }
            }
        }
    }
}
