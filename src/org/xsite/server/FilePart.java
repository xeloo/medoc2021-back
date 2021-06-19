package org.xsite.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.http.Part;

import org.xsite.webapp.WebApp;

public class FilePart {

    public final String name;
    public final String fileName;
    public final String contentType;
    public final long size;

    private final Part part;
    private boolean saved;

    public FilePart(Part part) {
        this.part = part;
        name = part.getName();
        fileName = part.getSubmittedFileName();
        contentType = part.getContentType();
        size = part.getSize();
    }

    public InputStream stream() throws IOException {
        return part.getInputStream();
    }

    public Path save(String dest) throws IOException {
        return save(Path.of(dest));
    }

    public Path save(Path dest) throws IOException {
        if( saved )
            throw new RuntimeException("Already saved !");

        WebApp webApp = Context.current().webApp();
        if( !dest.isAbsolute() ) {
            dest = webApp.uploadPath(dest);
        }
        Files.createDirectories(dest.getParent());
        part.write(webApp.tempDir().relativize(dest).toString());
        return dest;
    }

    public void delete() {
        try {
            part.delete();
        } catch( IOException ignored ) {
        }
    }

}
