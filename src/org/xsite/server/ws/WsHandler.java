package org.xsite.server.ws;

public class WsHandler {

    ConnectHandler connectHandler;
    MessageHandler messageHandler;
    BinaryHandler binaryHandler;
    CloseHandler closeHandler;
    ErrorHandler errorHandler;

    public WsHandler connect(ConnectHandler handler) {
        connectHandler = handler;
        return this;
    }

    public WsHandler message(MessageHandler handler) {
        messageHandler = handler;
        return this;
    }

    public WsHandler binary(BinaryHandler handler) {
        binaryHandler = handler;
        return this;
    }

    public WsHandler close(CloseHandler handler) {
        closeHandler = handler;
        return this;
    }

    public WsHandler error(ErrorHandler handler) {
        errorHandler = handler;
        return this;
    }


    interface Handler<C extends WsContext> {
        void handle(C ws);
    }

    public interface ConnectHandler extends Handler<WsConnectContext> {
    }

    public interface MessageHandler extends Handler<WsMessageContext> {
    }

    public interface BinaryHandler extends Handler<WsBinaryContext> {
    }

    public interface ErrorHandler extends Handler<WsErrorContext> {
    }

    public interface CloseHandler extends Handler<WsCloseContext> {
    }

}
