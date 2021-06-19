package org.xsite.server;

import java.nio.file.Path;

import javax.servlet.http.HttpServlet;

public interface HttpServerImpl {

    String getName();

    void start(int port, HttpServlet httpServlet);

    void stop();

    void sendStaticResource(Path path);
}
