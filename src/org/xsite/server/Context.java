package org.xsite.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.xsite.webapp.WebApp;

public class Context {

    private static final ThreadLocal<Context> currentContext = new ThreadLocal<>();
    private static final String MULTIPART_FORM_DATA = "multipart/form-data";

    public final HttpServletRequest req;
    public final HttpServletResponse res;
    public final HttpMethod method;
    public final String contentType;
    public final String path;
    private final HttpServerImpl server;
    private final Map<Object, Object> attributes = new HashMap<>();
    private final String sessionId;
    private Session session;
    private final WebApp webApp;
    private boolean completed;

    private Map<String, String[]> paramMap;
    private Map<String, FilePart> fileMap;

    public Context(HttpServletRequest request, HttpServletResponse response, HttpServerImpl server, WebApp webApp) {
        req = request;
        res = response;
        method = HttpMethod.valueOf(req.getMethod().toUpperCase());
        contentType = req.getContentType();
        path = req.getPathInfo();
        this.server = server;
        this.webApp = webApp.resolveWebApp(this);
        sessionId = req.getSession().getId();
        session = Session.getSession(sessionId);
    }

    public void activate() {
        currentContext.set(this);
        if( session != null )
            session.lastAccess = System.currentTimeMillis();
    }

    public void deactivate() {
        currentContext.set(null);
    }

    public static Context current() {
        return currentContext.get();
    }

    public HttpMethod method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String header(String name) {
        return req.getHeader(name);
    }

    public Context header(String name, String value) {
        res.setHeader(name, value);
        return this;
    }

    public Context contentType(String contentType) {
        res.setContentType(contentType);
        return this;
    }

    public Session session() {
        return session;
    }

    public Session newSession() {
        return session = Session.getOrCreate(sessionId);
    }

    public <T> T get(Object key) {
        return (T)attributes.computeIfAbsent(key, webApp::createValue);
    }

    public <T> Context set(Object key, T value) {
        attributes.put(key, value);
        return this;
    }

    public <T> Context set(T value) {
        if( value != null ) {
            attributes.put(value.getClass(), value);
        }
        return this;
    }

    public String body() throws IOException {
        StringWriter sw = new StringWriter();
        req.getReader().transferTo(sw);
        return sw.toString();
    }

    // TODO: REWORK IT !
    public String param(String name) {
//        return req.getParameter(name);
        String[] values = paramMap().get(name);
        return values != null ? values[0] : null;
    }

    public void param(String name, String value) {
        paramMap().put(name, new String[]{value});
    }

    public Map<String, String[]> paramMap() {
        if( paramMap == null ) {
            // TODO: optimize it!
            // multipart/form-data; boundary=---------------------------306380905110154171442531971803
//            req.getPart("").
            paramMap = new HashMap<>(req.getParameterMap());
        }
        return paramMap;
    }

    public FilePart file(String name) {
        try {
            if( fileMap == null ) {
                if( method == HttpMethod.POST && contentType.startsWith(MULTIPART_FORM_DATA) ) {
                    Collection<Part> parts = req.getParts();
                    if( parts != null ) {
                        fileMap = new HashMap<>();
                        for( Part part : parts ) {
                            if( part.getSubmittedFileName() != null ) {
                                fileMap.put(part.getName(), new FilePart(part));
                            }
                        }
                    } else {
                        fileMap = Map.of();
                    }
                } else {
                    fileMap = Map.of();
                }
            }

            return fileMap.get(name);
        } catch( Exception e ) {
            e.printStackTrace();
            return null;
        }
    }

    public Context status(int status) {
        res.setStatus(status);
        return this;
    }

    public void send(Path resource) {
        if( completed ) {
            new IllegalStateException("XSITE: Request already completed!").printStackTrace();
            return;
        }
        if( !Files.isRegularFile(resource) ) {
            resource = webApp.findResource(resource);
        }
        server.sendStaticResource(resource);
        completed = true;
    }

    public void send(String result) {
        if( completed ) {
            new IllegalStateException("XSITE: Request already completed!").printStackTrace();
            return;
        }
        try {
            res.getWriter().write(result);
        } catch( IOException e ) {
            e.printStackTrace();
        }
        completed = true;
    }

    public void send(InputStream stream) {
        if( completed ) {
            new IllegalStateException("XSITE: Request already completed!").printStackTrace();
            return;
        }
        try {
            stream.transferTo(res.getOutputStream());
        } catch( EOFException ignored ) {
            // Transfer interrupted by remote host
        } catch( IOException e ) {
            e.printStackTrace();
        }
        completed = true;
    }

    public void redirect(String location) {
        redirect(location, HttpServletResponse.SC_MOVED_TEMPORARILY);
    }
    public void redirect(String location, int httpStatusCode) {
        if( completed ) {
            new IllegalStateException("XSITE: Request already completed!").printStackTrace();
            return;
        }
        res.setHeader("Location", location);
        res.setStatus(httpStatusCode);
        completed = true;
        throw new ResponseException();
    }


    public WebApp webApp() {
        return webApp;
    }

    public boolean completed() {
        return completed;
    }
}
