package org.xsite.server.jetty;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.xsite.server.Context;
import org.xsite.server.ws.WsHandler;


public class JettyWsServlet extends WebSocketServlet {

    private static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.setCreator((req, res) -> {
            Context ctx = Context.current();
            return new JettyWsChannel();
        });
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        if( req.getHeader(SEC_WEBSOCKET_KEY) != null ) {
            String path = req.getRequestURI();
            if( !path.endsWith("/") )
                path += "/";

            Context ctx = Context.current();
            WsHandler wsHandler = ctx.webApp().findWsHandler(path);
            if( wsHandler != null ) {
                ctx.set(wsHandler);
                super.service(req, res);
                return;
            }
        }
        res.sendError(404, "WebSocket handler not found");
    }

}
