package org.xsite.server.ws;

import ru.xeloo.json.JSON;

public class WsMessageContext extends WsContext {
    public final String message;

    public WsMessageContext(WsChannel channel, String message) {
        super(channel);
        this.message = message;
    }

    public String message() {
        return message;
    }

    public <T> T message(Class<T> type) {
        return JSON.parse(type, message);
    }
}
