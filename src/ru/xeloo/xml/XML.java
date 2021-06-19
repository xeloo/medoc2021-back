package ru.xeloo.xml;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Objects;

public class XML {

    public static boolean DUMP_FIELD_NOT_FOUND = false;

    //    private static final DateFormat DF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    static final DateFormat DF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    static final DateFormat[] DF_ALT = new DateFormat[]{
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    };

    static final char[] TRUE = {'t', 'r', 'u', 'e'};
    static final char[] FALSE = {'f', 'a', 'l', 's', 'e'};
    static final char[] NULL = {'n', 'u', 'l', 'l'};

    private static final byte[] XML_HEADER = {'<', '?', 'x', 'm', 'l', ' '};
    private static final int XML_HEADER_LENGTH = XML_HEADER.length;
    private static final byte[] XML_ENCODING = {'e', 'n', 'c', 'o', 'd', 'i', 'n', 'g'};
    private static final int XML_ENCODING_LENGTH = XML_ENCODING.length;


    public static <T> T read(Class<T> type, Path file) throws IOException {
        return parse(type, Files.readAllBytes(file));
    }

    public static <T> T read(Class<T> type, Path file, Charset charset) throws IOException {
        return parse(type, Files.readString(file, charset));
    }

    public static void write(Object obj, Path file) throws IOException {
        Files.writeString(file, serialize(obj));
    }

    public static void write(Object obj, Path file, Charset charset) throws IOException {
        Files.writeString(file, new XMLWriter(charset.name()).serialize(obj), charset);
    }

    public static <T> T parse(Class<T> type, byte[] bytes) throws IOException {
        if( bytes == null )
            return null;

        return (T)new XMLParser(new String(bytes, getCharset(bytes))).parse(type);
    }

    public static <T> T parse(Class<T> type, String xml) {
        if( xml == null )
            return null;

        return (T)new XMLParser(xml).parse(type);
    }

    public static String serialize(Object obj) {
        return new XMLWriter().serialize(obj);
    }

    private static Charset getCharset(byte[] bytes) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        int len = bytes.length;
        if( Arrays.equals(bytes, 0, XML_HEADER_LENGTH, XML_HEADER, 0, XML_HEADER_LENGTH) ) {
            for( int i = XML_HEADER_LENGTH; i < len; i++ ) {
                if( bytes[i] == '?' ) {
                    break;
                }
                if( bytes[i] == 'e' && Arrays.equals(bytes, i, i + XML_ENCODING_LENGTH, XML_ENCODING, 0, XML_ENCODING_LENGTH) ) {
                    i += XML_ENCODING_LENGTH;
                    len = i + 8;
                    while( i < len ) {
                        byte b = bytes[i];
                        if( b == '"' || b == '\'' ) {
                            byte quot = b;
                            int s = ++i;
                            len = s + 16;
                            while( true ) {
                                if( i > len ) {
                                    throw new IOException("Undefined charset at " + s);
                                }
                                b = bytes[i];
                                if( b == quot ) {
                                    charset = Charset.forName(new String(bytes, s, i - s));
                                    break;
                                }
                                i++;
                            }
                            break;
                        }
                        i++;
                    }
                    break;
                }
            }
        }
        return charset;
    }


    public static class DOM {

    }

    static class Name {
        String ns;
        String name;

        public Name(String name) {
            String[] pair = name.split(":");
            if( pair.length == 1 ) {
                this.ns = null;
                this.name = name;
            } else if( pair.length == 2 ) {
                this.ns = pair[0];
                this.name = pair[1];
            } else {
                throw new IllegalArgumentException("Invalid attribute/tag name: " + name);
            }
        }

        public Name(String ns, String name) {
            this.ns = ns;
            this.name = name;
        }

        @Override
        public String toString() {
            return (ns != null ? ns + ':' : "") + name;
        }

        @Override
        public boolean equals(Object o) {
            if( this == o ) return true;
            if( o == null || getClass() != o.getClass() ) return false;
            Name n = (Name)o;
            return (n.ns == null || Objects.equals(ns, n.ns)) && Objects.equals(name, n.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ns, name);
        }
    }

    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Inherit {
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NS {
        String[] value();
    }

    @Inherited
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Attr {
        String value() default "";
    }

    @Inherited
    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Tag {
        String value() default "";
    }

    @Inherited
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface List {
        String tag() default "";

        String item();

        boolean inline() default false;
    }

    @Inherited
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Hidden {
    }

    @Inherited
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Inline {
    }

}
