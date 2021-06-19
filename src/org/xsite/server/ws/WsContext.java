package org.xsite.server.ws;

import org.xsite.server.Context;

import ru.xeloo.json.JSON;

public class WsContext {

    public final WsChannel channel;
    public final Context context;

    public WsContext(WsChannel channel) {
        this.channel = channel;
        context = channel.context;
    }

    public void send(Object obj) {
        send(JSON.stringify(obj));
    }

    public void send(String message) {
        channel.send(message);
    }

    public void send(byte[] data) {
        channel.send(data);
    }

}
