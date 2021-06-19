package org.xsite.server.jetty;

import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.xsite.server.ws.WsChannel;

public class JettyWsChannel extends WsChannel implements WebSocketListener {

    private Session session;


    @Override
    public Future<Void> send(String message) {
        return session.getRemote().sendStringByFuture(message);
    }

    @Override
    public Future<Void> send(byte[] data) {
        return session.getRemote().sendBytesByFuture(ByteBuffer.wrap(data));
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        if( offset != 0 || len != payload.length ) {
            System.out.println("!!!!!!!!!! JettyWsChannel.onWebSocketBinary()  ::  Invalid offset and length");
        }
        binary(payload);
    }

    @Override
    public void onWebSocketText(String message) {
        message(message);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        close(statusCode, reason);
    }

    @Override
    public void onWebSocketConnect(Session session) {
        this.session = session;
        connect(this);
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        error(cause);
    }
}
