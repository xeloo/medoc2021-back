package org.xsite.webapp;

import java.nio.file.Path;


public class SimpleWebApp extends WebApp {


    public SimpleWebApp(Path contentDir) {
        addBaseDir(contentDir.resolve(ASSETS_DIR));
        addBaseDir(contentDir.resolve(UPLOAD_DIR));
    }

}
