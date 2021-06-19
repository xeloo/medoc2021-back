package org.xsite.server.ws;

import java.util.concurrent.Future;

import org.xsite.server.Context;

public abstract class WsChannel {

    public final Context context;
    final WsHandler handler;

    public WsChannel() {
        context = Context.current();
        handler = context.get(WsHandler.class);
    }

    public abstract Future<Void> send(String message);

    public abstract Future<Void> send(byte[] data);

    private <C extends WsContext, H extends WsHandler.Handler<C>> void callHandler(C ctx, H handler) {
        if( handler != null ) {
            context.activate();
            handler.handle(ctx);
            context.deactivate();
        }
    }


    public void connect(WsChannel channel) {
        callHandler(new WsConnectContext(this), handler.connectHandler);
    }

    public void message(String msg) {
        callHandler(new WsMessageContext(this, msg), handler.messageHandler);
    }

    public void binary(byte[] data) {
        callHandler(new WsBinaryContext(this, data), handler.binaryHandler);
    }

    public void close(int status, String reason) {
        callHandler(new WsCloseContext(this, status, reason), handler.closeHandler);
    }

    public void error(Throwable error) {
        callHandler(new WsErrorContext(this, error), handler.errorHandler);
    }

}
