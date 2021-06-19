package org.xsite.webapp;

import org.xsite.server.Context;

public interface RequestHandler {

    void handle(Context ctx);

}
