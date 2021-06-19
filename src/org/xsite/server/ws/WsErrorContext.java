package org.xsite.server.ws;

public class WsErrorContext extends WsContext {
    public final Throwable error;

    public WsErrorContext(WsChannel channel, Throwable error) {
        super(channel);
        this.error = error;
    }
}
