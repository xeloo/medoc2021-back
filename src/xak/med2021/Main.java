package xak.med2021;

import java.nio.file.Path;

import org.xsite.server.HttpServer;
import org.xsite.webapp.WebApp;

import xak.med2021.api.AuthModule;
import xak.med2021.api.RefModule;

public class Main {

    public static final int PORT = 9080;

    public static RestClient rsClient;

    public static void main(String[] args) {
        new HttpServer()
            .app(new WebApp() {{
                new AuthModule();
                new RefModule();
                new RestApi();

                addBaseDir(Path.of("web/dist"));
                addBaseDir(Path.of("web"));
            }})
            .port(PORT)
            .start();
    }

}
