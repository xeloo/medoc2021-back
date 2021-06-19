package org.xsite.server.ws;

public class WsCloseContext extends WsContext {
    public final int status;
    public final String reason;

    public WsCloseContext(WsChannel channel, int status, String reason) {
        super(channel);
        this.status = status;
        this.reason = reason;
    }
}
