package ru.xeloo.http.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * @version 14/05/2016  -  Add download method
 * <p>
 * TODO: Split cookies for different domains
 * TODO: Async requests
 */
@SuppressWarnings("ALL")
public class HttpRequest {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:52.0) Gecko/20100101 Firefox/63.0";

    private static final SimpleDateFormat COOKIE_DF = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    private static final String CRLF = "\r\n";
    private static final String TEXT_PLAIN = "text/plain";
    //	private static final SimpleDateFormat COOKIE_DF_2 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

    private Map<String, String> headers;

    /**
     * Last url used for referer header
     */
    public String lastUrl = null;
    public String baseUrl = null;
    public boolean updateReferrer = true;
    private boolean followRedirect = false;

    public int TIMEOUT = 5000;

    public String defaultCharset = "UTF-8";
    public String cookies_;
    public Map<String, String> cookies = new HashMap<>();

    public int requestCount;
    private boolean stopDownload;

    {
        headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "ru-ru,ru;q=0.8,en-us;q=0.5,en;q=0.3");
        headers.put("Accept-Encoding", "gzip, deflate");
        headers.put("Accept-Charset", "windows-1251,utf-8;q=0.7,*;q=0.7");
        headers.put("Connection", "keep-alive");

        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    }

    public HttpRequest() {
    }

    public HttpRequest(int timeout) {
        this.TIMEOUT = timeout;
    }

    public HttpRequest(String lastUrl) {
        this.lastUrl = lastUrl;
    }

    public HttpRequest timeout(int timeout) {
        this.TIMEOUT = timeout;
        return this;
    }

    public HttpRequest followRedirect(boolean follow) {
        followRedirect = follow;
        return this;
    }

    public HttpRequest baseUrl(String url) {
        if( !url.endsWith("/") )
            url += "/";
        baseUrl = url;
        return this;
    }

    public HttpRequest referrer(String referrer) {
        this.lastUrl = referrer;
        return this;
    }

    public HttpRequest referrer(String referrer, boolean update) {
        this.lastUrl = referrer;
        this.updateReferrer = update;
        return this;
    }

    public HttpRequest charset(String charset) {
        defaultCharset = charset;
        return this;
    }

    public HttpRequest cookies(String cookies) {
        this.cookies_ = cookies;
        for( String part : cookies.split(";") ) {
            int eq = part.indexOf('=');
            this.cookies.put((eq < 0 ? part : part.substring(0, eq)).trim(), eq < 0 ? "" : part.substring(eq + 1));
        }
        return this;
    }

    public HttpRequest cookies(String name, String value) {
        cookies.put(name, value);
        cookies_ = null;
        return this;
    }

    public String getCookie(String name) {
        return cookies.get(name);
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public void setHeader(String name, String value) {
        if( value != null ) {
            headers.put(name, value);
        } else {
            headers.remove(name);
        }
    }

    public void removeHeader(String name) {
        headers.remove(name);
    }

    public Response put(String url, String query) throws IOException {
        return post("PUT", url, query, null);
    }

    public Response post(String url, String query) throws IOException {
        return post("POST", url, query, null);
    }

    public Response put(String url, Map<String, ?> params) throws IOException {
        return post("PUT", url, paramsToString(params), null);
    }

    public Response post(String url, Map<String, ?> params) throws IOException {
        return post("POST", url, paramsToString(params), null);
    }

    public Response put(String url, String query, String contentType) throws IOException {
        return post("PUT", url, query, contentType);
    }

    public Response post(String url, String query, String contentType) throws IOException {
        return post("POST", url, query, contentType);
    }

    public Response put(String url, InputStream stream, String contentType) throws IOException {
        return post("PUT", url, stream, contentType);
    }

    public Response post(String url, InputStream stream, String contentType) throws IOException {
        return post("POST", url, stream, contentType);
    }

    private Response post(String method, String url, String query, String contentType) throws IOException {
        if( query == null ) query = "";
        HttpURLConnection conn = createConnection(url, null);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", contentType != null ? contentType : "application/x-www-form-urlencoded; charset=" + defaultCharset);
        conn.setRequestProperty("Content-Length", String.valueOf(query.length()));
//		dumpMap("REQUEST HEADERS:", conn.getRequestProperties());
        conn.setDoOutput(true);
        conn.getOutputStream().write(query.getBytes(defaultCharset));

        return getResponse(conn);
    }

    private Response post(String method, String url, InputStream stream, String contentType) throws IOException {
        HttpURLConnection conn = createConnection(url, null);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", contentType);
//        conn.setRequestProperty("Content-Length", String.valueOf(query.length()));
//		dumpMap("REQUEST HEADERS:", conn.getRequestProperties());
        conn.setDoOutput(true);
        stream.transferTo(conn.getOutputStream());

        return getResponse(conn);
    }

    public Response put(String url, Part... parts) throws IOException {
        return multipart("PUT", url, parts);
    }

    public Response post(String url, Part... parts) throws IOException {
        return multipart("POST", url, parts);
    }

    @Deprecated
    public Response multipart(String url, Part... parts) throws IOException {
        return multipart("POST", url, parts);
    }

    private Response multipart(String method, String url, Part... parts) throws IOException {
        HttpURLConnection conn = createConnection(url, null);
        conn.setRequestMethod(method);
        String boundary = Long.toHexString(System.currentTimeMillis());
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);

        try(
            OutputStream output = conn.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, defaultCharset));
        ) {
            for( Part part : parts ) {
                writer.append("--").append(boundary).append(CRLF)
                      .append("Content-Disposition: form-data; name=\"").append(part.name).append("\"");
                if( part.filename != null ) {
                    writer.append("; filename=\"").append(part.filename).append("\"");
                }
                writer.append(CRLF).append("Content-Type: ").append(part.contentType);
                if( part.charset != null ) {
                    writer.append("; charset=").append(part.charset);
                }
                writer.append(CRLF).append(CRLF);
                if( part.stream != null ) {
                    writer.flush();
                    part.stream.transferTo(output);
                    output.flush();
                } else if( part.file != null ) {
                    writer.flush();
                    Files.copy(part.file, output);
                    output.flush();
                } else {
                    writer.append(part.value);
                }
                writer.append(CRLF);
            }

            writer.append("--").append(boundary).append("--").append(CRLF).flush();
        }

        return new Response(this, conn);
    }

    public static class Part {
        public String name;
        public String value;
        public String contentType;
        public String charset;
        public String filename;
        public Path file;
        public InputStream stream;

        public Part(String name, String value) {
            this(name, null, value);
        }

        public Part(String name, String filename, String value) {
            this.name = name;
            this.value = value;
            filename(filename);
        }

        public Part(String name, Path file) {
            this.name = name;
            this.file = file;
            filename(file.getFileName().toString());
        }

        public Part(String name, InputStream stream) {
            this(name, null, stream);
        }

        public Part(String name, String filename, InputStream stream) {
            this.name = name;
            this.stream = stream;
            filename(filename);
        }

        public Part filename(String filename) {
            if( filename != null ) {
                this.filename = filename;
                // TODO: replace with local implementation
                contentType = URLConnection.guessContentTypeFromName(filename);
            }
            return this;
        }

        public Part contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Part charset(String charset) {
            this.charset = charset;
            return this;
        }
    }

    private String buildUrl(String url) {
        if( baseUrl != null ) {
            url = baseUrl + (url.startsWith("/") ? url.substring(1) : url);
        }
        return url;
    }

    public Response options(String url) throws IOException {
        HttpURLConnection conn = createConnection(url, null);
        conn.setRequestMethod("OPTION");
        return getResponse(conn);
    }

    public Response delete(String url) throws IOException {
        HttpURLConnection conn = createConnection(url, null);
        conn.setRequestMethod("DELETE");
        return getResponse(conn);
    }

    public Response get(String url) throws IOException {
        return getResponse(createConnection(url, null));
    }

    public Response get(String url, Map<String, ?> params) throws IOException {
        String query = paramsToString(params);
        if( query != null ) {
            url += "?" + query;
        }
        return getResponse(createConnection(url, null));
    }

    public Response get(String url, String accept) throws IOException {
        return getResponse(createConnection(url, accept));
    }

    private String paramsToString(Map<String, ?> params) throws IOException {
        if( params != null && !params.isEmpty() ) {
            StringBuilder sb = new StringBuilder();
            for( Map.Entry p : params.entrySet() ) {
                sb.append('&').append(p.getKey()).append('=').append(URLEncoder.encode(p.getValue().toString(), defaultCharset));
            }
            return sb.substring(1);
        }
        return null;
    }

    public void stopDownload() {
        stopDownload = true;
    }

    public boolean download(String url, File file) throws IOException {
        return _download(url, file, false);
    }

    public boolean download(String url, File file, boolean restart) throws IOException {
        stopDownload = false;
        System.out.println("Start download '" + file.getName() + "'");
        long wait = 0;
        while( true ) {
            if( stopDownload )
                return false;
            long time = System.currentTimeMillis();
            if( _download(url, file, restart) )
                return true;
            if( stopDownload )
                return false;

            if( System.currentTimeMillis() - time < 10000 ) {
                wait += 5000;
                if( wait > 300000 )
                    wait = 300000;
            } else {
                wait = 5000;
            }

            try {
                System.out.println("Wait '" + file.getName() + "' for " + (wait / 1000) + " sec");
                Thread.sleep(wait);
            } catch( InterruptedException ignored ) {
            }
        }
    }

    private boolean _download(String url, File file, boolean restart) throws IOException {
        HttpURLConnection conn = createConnection(url);
        long length = file.length();
        restart = restart || length == 0;
        if( !restart ) {
            conn.setRequestProperty("Range", "bytes=" + length + "-");
            System.out.println("Continue download '" + file.getName() + "' from " + length);
        }
        try {
            int code = conn.getResponseCode();
            if( code == 416 ) {
                // No more content
                return true;
            } else if( code / 100 == 3 ) { // 3xx
                updateCookies(conn);
                throw new RedirectException(conn.getHeaderField("Location"), conn.getResponseMessage());
            } else if( code != 200 && code != 206 ) {
                throw new IOException(code + ": " + conn.getResponseMessage() + ": " + conn.getRequestMethod() + " " + conn.getURL());
            }

            updateCookies(conn);
        } catch( SocketTimeoutException e ) {
            return false;
        }
        // Partial Content

        InputStream stream = conn.getInputStream();
        if( "gzip".equals(conn.getContentEncoding()) )
            stream = new GZIPInputStream(stream);

        FileOutputStream out = new FileOutputStream(file, !restart);
        byte[] buf = new byte[1024];
        try {
            long time = System.currentTimeMillis();
            long bytes = 0;
            while( true ) {
                if( stopDownload )
                    return false;

                int n = stream.read(buf);
                if( n == -1 ) {
                    out.flush();
                    break;
                }
                out.write(buf, 0, n);
                length += n;
                bytes += n;
                if( System.currentTimeMillis() - time > 60000 ) {
                    out.flush();
                    System.out.println("Downloading... '" + file.getName() + "'   " + length + " bytes,   " + (bytes / (System.currentTimeMillis() - time)) + " Kb/s");
                    time = System.currentTimeMillis();
                    bytes = 0;
                }
            }
        } catch( IOException e ) {
            return false;
        } finally {
            try {
                out.close();
            } catch( Exception ignored ) {
            }
            try {
                stream.close();
            } catch( IOException ignored ) {
            }
        }

        String range = conn.getHeaderField("Content-Range");
        return range == null || file.length() == Long.parseLong(range.substring(range.lastIndexOf('/') + 1));
    }


    public HttpURLConnection createConnection(String url) throws IOException {
        return createConnection(url, null);
    }

    public HttpURLConnection createConnection(String url, String accept) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(buildUrl(url)).openConnection();
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setInstanceFollowRedirects(followRedirect);
        setRequestHeaders(conn, accept);
        if( cookies_ == null && cookies.size() > 0 ) {
            StringBuilder sb = new StringBuilder();
            for( Map.Entry<String, String> cookie : cookies.entrySet() ) {
                sb.append(cookie.getKey()).append('=').append(cookie.getValue()).append("; ");
            }
            sb.setLength(sb.length() - 2);
            cookies_ = sb.toString();
        }
        if( cookies_ != null )
            conn.setRequestProperty("Cookie", cookies_);
        if( lastUrl != null )
            conn.setRequestProperty("Referer", lastUrl);

        requestCount++;
        return conn;
    }

    private Response getResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        if( code >= 400 && code < 500 ) {
            fuckResponseCode(conn, 200);
        }

        if( code == 200 ) {
            updateCookies(conn);
            if( updateReferrer )
                lastUrl = conn.getURL().toString();

        } else if( code / 100 == 3 ) { // 3xx
            updateCookies(conn);
            if( updateReferrer )
                lastUrl = conn.getURL().toString();

            String location = conn.getHeaderField("Location");
            System.out.println("Redirect to '" + location + "'");
            throw new RedirectException(location, conn.getResponseMessage());
        }

        return new Response(this, conn);
    }

    private void fuckResponseCode(HttpURLConnection conn, int code) {
        try {
//            Field field = conn.getClass().getDeclaredField("delegate");
//            field.setAccessible(true);
//            Object delegate = field.get(conn);

//            field = HttpURLConnection.class.getDeclaredField("responseCode");
//            field.setAccessible(true);
//            field.setInt(delegate, code);

            Field field = conn.getClass().getDeclaredField("rememberedException");
            field.setAccessible(true);
            field.set(conn, null);

        } catch( Exception e ) {
			e.printStackTrace();
        }
    }

    private void updateCookies(HttpURLConnection conn) {
        long now = System.currentTimeMillis();
        for( Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet() ) {
            if( "Set-Cookie".equalsIgnoreCase(header.getKey()) ) {
                for( String cookie : header.getValue() ) {
                    String name = null;
                    String value = null;
                    String expDate = null;

                    for( String part : cookie.split(";") ) {
                        int eq = part.indexOf('=');
                        String key = (eq < 0 ? part : part.substring(0, eq)).trim();
                        String low = key.toLowerCase();
                        if( low.equals("expires") ) {
                            expDate = eq < 0 ? "" : part.substring(eq + 1);
                        } else if( !low.equals("domain") && !low.equals("path") && !low.equals("secure") && !low.equals("httponly") && !low.equals("max-age") ) {
                            name = key;
                            value = eq < 0 ? "" : part.substring(eq + 1);
                        }
                    }

                    boolean expired = false;
                    try {
                        expired = expDate != null && COOKIE_DF.parse(expDate.replace('-', ' ')).getTime() < now;
                    } catch( ParseException e ) {
                        System.out.println(e.getMessage());
                    }
                    if( expired ) {
                        cookies.remove(name);
                    } else {
                        cookies.put(name, value);
                    }
//					System.out.println("Set-Cookie: " + cookie);
                    cookies_ = null;
                }
            }
        }
    }


    private void setRequestHeaders(HttpURLConnection conn, String accept) {
//		conn.setRequestProperty("Host", conn.getRequestProperty("Host"));
        conn.setRequestProperty("Host", conn.getURL().getHost());
        for( Map.Entry<String, String> entry : headers.entrySet() ) {
            conn.setRequestProperty(entry.getKey(), entry.getValue());
        }
        if( accept != null ) {
            conn.setRequestProperty("Accept", accept);
        }
    }

    public void dumpCookies() {
        dumpMap("COOKIES:", cookies);
    }

    private void dumpMap(String title, Map<String, ?> map) {
        System.out.println(title);
        for( Map.Entry<String, ?> entry : map.entrySet() ) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println();
    }
}
