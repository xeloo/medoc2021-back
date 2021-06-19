package org.xsite.server.ws;

public class WsBinaryContext extends WsContext {
    public final byte[] data;

    public WsBinaryContext(WsChannel channel, byte[] data) {
        super(channel);
        this.data = data;
    }
}
