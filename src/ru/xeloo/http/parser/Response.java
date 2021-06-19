package ru.xeloo.http.parser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Response {

    private final HttpRequest http;
    private final HttpURLConnection conn;
    public final int code;
    public final String message;
    public final Map<String, List<String>> headers;

    Response(HttpRequest http, HttpURLConnection conn) throws IOException {
        this.http = http;
        this.conn = conn;
        code = conn.getResponseCode();
        message = conn.getResponseMessage();
        headers = conn.getHeaderFields();

        if( code / 100 != 2 )
            System.out.println(code + " " + conn.getResponseMessage() + ": " + conn.getRequestMethod() + " " + conn.getURL());

    }

    public String header(String name) {
        if( headers != null ) {
            List<String> values = headers.get(name);
            return values.get(0);
        }
        return null;
    }

    public InputStream inputStream() throws IOException {
        InputStream stream;
        try {
            stream = conn.getInputStream();
        } catch( IOException e ) {
            stream = conn.getErrorStream();
        }

        String encoding = conn.getContentEncoding();
        if( "gzip".equals(encoding) )
            stream = new GZIPInputStream(stream);

        return stream;
    }

    public Reader reader() throws IOException {
        return new InputStreamReader(inputStream(), getCharset(conn.getContentType()));
    }

    public byte[] bytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(conn.getHeaderFieldInt("Content-Length", 1024));
        try( InputStream stream = inputStream() ) {
            stream.transferTo(out);
        }
        return out.toByteArray();
    }

    public Parser parser() throws IOException {
        return new Parser(reader());
    }

    public String string() throws IOException {
        Reader reader = reader();
        StringBuilder sb = new StringBuilder();
        char[] buff = new char[1024];
        while( true ) {
            int n = reader.read(buff);
            if( n == -1 )
                break;
            sb.append(buff, 0, n);
        }
        reader.close();
        return sb.toString();
    }


    public void transferTo(Writer out) throws IOException {
        try( Reader reader = reader() ) {
            reader.transferTo(out);
        }
    }

    public void transferTo(OutputStream out) throws IOException {
        try( InputStream stream = inputStream() ) {
            stream.transferTo(out);
        }
    }

    public void print() throws IOException {
        transferTo(new PrintWriter(System.out));
    }

    public void save(String fileName) throws IOException {
        save(Path.of(fileName));
    }

    public void save(File file) throws IOException {
        save(file.toPath());
    }

    public void save(Path file) throws IOException {
        try( OutputStream out = Files.newOutputStream(file) ) {
            transferTo(out);
        }
    }


    private String getCharset(String contentType) {
        if( contentType != null ) {
            int i = contentType.indexOf("charset=");
            if( i != -1 ) {
                String charset = contentType.substring(i + 8);
                if( charset.length() > 2 && charset.charAt(0) == '"' && charset.charAt(charset.length() - 1) == '"' ) {
                    charset = charset.substring(1, charset.length() - 2);
                }
                try {
                    if( Charset.isSupported(charset) )
                        return charset;
                } catch( Exception ignored ) {
                }
            }
        }
        return http.defaultCharset;
    }

}
