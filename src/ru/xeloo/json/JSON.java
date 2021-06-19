package ru.xeloo.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import ru.xeloo.io.CharBuffer;

import static java.lang.Math.min;

/**
 * <b>TODO:</b><br>
 * - Parse stream<br>
 * <br>
 */
public class JSON {

    protected static final char EOS = (char)-1;

    public static int COUNTER = 0;

    public static boolean DUMP_FIELD_NOT_FOUND = false;

    private static final DateFormat DF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
//    private static final DateFormat DF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final DateFormat DF_NO_MS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    private static final DateFormat DF_OUT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    static final DateFormat[] DF_ALT = new DateFormat[]{
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm"),
    };

    private static final char[] TRUE = {'t', 'r', 'u', 'e'};
    private static final char[] FALSE = {'f', 'a', 'l', 's', 'e'};
    private static final char[] NULL = {'n', 'u', 'l', 'l'};
    static final char[] QUOTE_COLUMN = {'"', ':'};


    private String str;

    private static final int BUFF_SIZE = 4000;
    //    private static final int BUFF_SIZE = 100;
    // TODO: use pool
//    private static final char[] buf = new char[BUFF_SIZE];
    private final char[] buf = new char[BUFF_SIZE];
    private int strLen;
    private int strPos; // string position of buffer begin;
    private int nextPos; // string position of next buffer fetch;
    private int bufPos; // position of current char
    private int mark;

    String scope;

    public JSON() {
    }

    public JSON(String json) {
        this(json, 0);
    }

    public JSON(String json, int pos) {
        if( json.charAt(pos) == '\ufeff' ) {
            pos++;
        }
        setReadBuffer(json, pos);
    }

    private void setReadBuffer(String str, int pos) {
        this.str = str;
        strLen = str.length();
        nextPos = pos;
        bufPos = BUFF_SIZE;
        mark = -1;
        fetch();
    }

    private char nextChar() {
        try {
            return buf[++bufPos];
        } catch( ArrayIndexOutOfBoundsException ignored ) {
            if( nextPos >= strLen ) {
                return EOS;
            }
            fetch();
            return buf[++bufPos];
        }
    }

    private String next(int len) {
        if( bufPos + len >= buf.length ) {
            bufPos++;
            fetch();
        }

        int s = bufPos + 1;
        bufPos += len;
        return new String(buf, s, len);
    }

    private void fetch() {
        int copyTo;
        int copyLen = BUFF_SIZE - (mark != -1 ? mark : bufPos);
        int endPos = nextPos + (BUFF_SIZE - copyLen);
        if( endPos >= strLen ) {
            endPos = strLen;
            copyTo = BUFF_SIZE - (endPos - nextPos) - copyLen;
        } else {
            copyTo = 0;
        }

        if( copyLen > 0 ) {
            System.arraycopy(buf, BUFF_SIZE - copyLen, buf, copyTo, copyLen);
        }

        if( mark != -1 ) {
            bufPos = copyTo + bufPos - mark;
            mark = copyTo;
        } else {
            bufPos = copyTo;
        }

        str.getChars(nextPos, endPos, buf, copyTo + copyLen);
        strPos = nextPos - bufPos;
        nextPos = endPos;
        bufPos--;
    }

    private void skipWhitespaces() {
        while( true ) {
            try {
                char ch = buf[++bufPos];
                if( ch == '/' ) {
                    skipComment();
                } else if( ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r' ) {
                    bufPos--;
                    break;
                }
            } catch( ArrayIndexOutOfBoundsException ignored ) {
                if( nextPos >= strLen )
                    return;

                fetch();
            }
        }
    }

    private void skipComment() {
        // SKIP COMMENT
        char ch = nextChar();
        if( ch == '/' ) {
            // SINGLE LINE COMMENT
            while( nextChar() != '\n' ) ;
        } else if( ch == '*' ) {
            // MULTI LINE COMMENT
            while( true ) {
                if( nextChar() == '*' ) {
                    if( nextChar() == '/' ) {
                        break;
                    }
                    bufPos--;
                }
            }
        } else {
            throw new IllegalArgumentException("Invalid JSON object, expected comment at " + bufPos + ": " + getCurrentSubstring());
        }
    }

    private boolean checkValue(char[] test) {
        mark = bufPos;
        bufPos--;
        for( char ch : test ) {
            if( ch != nextChar() ) {
                bufPos = mark;
                mark = -1;
                return false;
            }
        }
        mark = -1;
        return true;
    }

    public static <T> T parse(Class<T> type, String json) {
        if( json == null )
            return null;

        return (T)new JSON(json).parse(type);
    }

    //    public static Map<String, Object> parse(String json) {
    public static <T extends Map<String, ?>> T parse(String json) {
        return (T)parse(Object.class, json);
    }

    public static <T> List<T> parseAsList(String json) {
        return parseAsList(null, json);
    }

    public static <T> List<T> parseAsList(Class<T> itemType, String json) {
        if( json == null )
            return null;

        return new JSON(json).parseAsList(itemType);
    }

    public static <T> T[] parseAsArray(Class<T> clazz, String json) {
        List<T> list = parseAsList(clazz, json);
        return list.toArray((T[])Array.newInstance(clazz, list.size()));
    }

    private <T> List<T> parseAsList(Type itemType) {
        skipWhitespaces();

        if( nextChar() != '[' )
            throw new IllegalArgumentException("Invalid JSON array, expected '[' at " + bufPos + ": " + getCurrentSubstring());

        try {
            List<T> list = new ArrayList<>();
            skipWhitespaces();
            if( nextChar() != ']' ) {
                bufPos--;
                while( true ) {
                    Object value = parse(itemType);
                    list.add((T)value);

                    skipWhitespaces();
                    char ch = nextChar();
                    if( ch == ',' ) {
                        skipWhitespaces();
                        continue;
                    }
                    if( ch == ']' )
                        break;

                    throw new IllegalArgumentException("Invalid JSON array, expected ',' or ']' at " + bufPos + ": " + getCurrentSubstring());
                }
            }
            return list;
        } catch( Exception e ) {
            e.printStackTrace();
            return null;
        }
    }


    private Object parseNumber(Type type) {
//        pos--;
        if( type != null ) {
            if( type == Integer.TYPE || type == Integer.class )
                return parseInteger();
            if( type == AtomicInteger.class )
                return new AtomicInteger(parseInteger());
            if( type == Long.TYPE || type == Long.class )
                return parseLong();
            if( type == Double.TYPE || type == Double.class )
                return parseDouble();
            if( type == Float.TYPE || type == Float.class )
                return parseFloat();
        }
        return parseNumber();
    }

    private Object parseTrue(Type type) {
        if( checkValue(TRUE) ) {
            if( type != null ) {
                if( type == Boolean.TYPE || type == Boolean.class )
                    return Boolean.TRUE;
                if( type == String.class )
                    return "true";
                if( type == Integer.TYPE || type == Integer.class )
                    return 1;
            }
            return Boolean.TRUE;
        } else {
            throw new IllegalArgumentException("Invalid JSON value at " + bufPos + ": " + getCurrentSubstring());
        }
    }

    private Object parseFalse(Type type) {
        if( checkValue(FALSE) ) {
            if( type != null ) {
                if( type == Boolean.TYPE || type == Boolean.class )
                    return Boolean.FALSE;
                if( type == String.class )
                    return "false";
                if( type == Integer.TYPE || type == Integer.class )
                    return 0;
            }
            return Boolean.FALSE;
        } else {
            throw new IllegalArgumentException("Invalid JSON value at " + bufPos + ": " + getCurrentSubstring());
        }
    }

    private Object parseNull(Type type) {
        if( checkValue(NULL) ) {
            return null;
        } else {
            throw new IllegalArgumentException("Invalid JSON value at " + bufPos + ": " + getCurrentSubstring());
        }
    }

    private byte[] parseByteArray() {
        // CONSIDER STRING VALUE AS HEX PRESENTATION OF BYTE ARRAY
/*
        byte[] bytes = new byte[value.length() / 2];
        for( int i = 0; i < bytes.length; i++ ) {
            final int pos = i << 1;
            bytes[i] = (byte)Integer.parseInt(value.substring(pos, pos + 2), 16);
        }
        return bytes;
*/
        // TODO: REWORK
        return null;
    }

    private Date parseDate() {
        String str = parseString();
        try {
            return DF.parse(str);
        } catch( ParseException ignored ) {
        }
        for( DateFormat df : DF_ALT ) {
            try {
                return df.parse(str);
            } catch( ParseException ignored ) {
            }
        }
        return null;
    }

    private Path parsePath() {
        return Path.of(parseString());
    }

    private char parseChar() {
        String str = parseString();
        return str.length() == 0 ? 0 : str.charAt(0);
    }

    private boolean parseBoolean() {
        mark = bufPos;
        boolean value;
        char quote = 0;
        char ch = nextChar();
        if( ch == '"' || ch == '\'' ) {
            quote = ch;
            ch = nextChar();
        }
        if( ch == 'f' ) {
            if( !checkValue(FALSE) ) {
                throw new IllegalArgumentException("Invalid JSON value at " + mark + ": " + getCurrentSubstring());
            }
            value = false;
        } else if( ch == 't' ) {
            if( !checkValue(TRUE) ) {
                throw new IllegalArgumentException("Invalid JSON value at " + mark + ": " + getCurrentSubstring());
            }
            value = true;
        } else if( ch == '1' ) {
            value = true;
        } else if( ch == '0' ) {
            value = true;
        } else {
            throw new IllegalArgumentException("Invalid JSON value at " + mark + ": " + getCurrentSubstring());
        }

        if( quote != 0 ) {
            ch = nextChar();
            if( ch != quote ) {
                throw new IllegalArgumentException("Invalid JSON value at " + mark + ": " + getCurrentSubstring());
            }
        }

        return value;
    }

    private Object parseString(Type type) {
        bufPos--;

        if( type == null || type == String.class )
            return parseString();
        if( type == Integer.TYPE || type == Integer.class )
            return parseInteger();
        if( type == AtomicInteger.class )
            return new AtomicInteger(parseInteger());
        if( type == Long.TYPE || type == Long.class )
            return parseLong();
        if( type == Double.TYPE || type == Double.class )
            return parseDouble();
        if( type == Boolean.TYPE || type == Boolean.class )
            return parseBoolean();
        if( type == Character.TYPE || type == Character.class )
            return parseChar();
        if( type == Date.class ) {
            return parseDate();
        }
        if( type == Path.class ) {
            return parsePath();
        }
        if( ((Class<?>)type).getComponentType() == byte.class ) {
            return parseByteArray();
        }
        return parseString();
    }

    private Object parse(Type type) {
        skipWhitespaces();

        char ch = nextChar();
        switch( ch ) {
            case '{': {
                return parseObject(type);
            }
            case '[': {
                return parseArray(type);
            }
            case '"':
            case '\'': {
                return parseString(type);
            }
            case 't': {
                return parseTrue(type);
            }
            case 'f': {
                return parseFalse(type);
            }
            case 'n': {
                return parseNull(type);
            }
            default: {
                if( ch >= '0' && ch <= '9' || ch == '-' ) {
                    bufPos--;
                    return parseNumber(type);
                }
                throw new IllegalArgumentException("Invalid JSON at " + bufPos + ": " + getCurrentSubstring());
            }
        }
    }

    private int parseInteger() {
        return (int)parseLong();
    }

    private long parseLong() {
        mark = bufPos;
        long val = 0;
        boolean minus = false;
        char quote = 0;

        char ch = nextChar();
        if( ch == '"' || ch == '\'' ) {
            quote = ch;
            ch = nextChar();
        }
        if( ch == '-' ) {
            minus = true;
            ch = nextChar();
        }

        while( ch >= '0' && ch <= '9' ) {
            val = val * 10 + (ch - '0');
            ch = nextChar();
        }
        if( quote != 0 ) {
            if( ch != quote ) {
                throw new IllegalArgumentException("Invalid JSON integer at " + mark + ": " + getCurrentSubstring());
            }
        } else {
            bufPos--;
        }
        mark = -1;
        return minus ? -val : val;
    }

    private float parseFloat() {
        return (float)parseDouble();
    }

    private double parseDouble() {
        mark = bufPos;
        char quote = 0;
        char ch = nextChar();
        if( ch == '"' || ch == '\'' ) {
            quote = ch;
        } else {
            bufPos--;
        }
        String val = parseNumber();

        if( quote != 0 ) {
            if( nextChar() != quote ) {
                throw new IllegalArgumentException("Invalid JSON number at " + mark + ": " + getCurrentSubstring());
            }
        }
        mark = -1;
        return Double.parseDouble(val);
    }

    private String parseNumber() {
        mark = bufPos + 1;
        boolean dot = false;
        boolean exp = false;

        char ch = nextChar();
        if( ch == '-' ) {
            ch = nextChar();
        }

        while( true ) {
            if( ch >= '0' && ch <= '9' ) {
            } else if( ch == '.' ) {
                if( dot )
                    throw new IllegalArgumentException("Invalid JSON number at " + mark + ": " + getCurrentSubstring());
                dot = true;
            } else if( ch == 'E' || ch == 'e' ) {
                if( exp )
                    throw new IllegalArgumentException("Invalid JSON number at " + mark + ": " + getCurrentSubstring());
                exp = true;
            } else {
                break;
            }
            ch = nextChar();
        }
        String val = str.substring(strPos + mark, strPos + bufPos);
        bufPos--;
        mark = -1;
        return val;
    }

    private Object parseArray(Type type) {
        try {
            boolean arr = false; // TRUE - if type is ARRAY
            List<Object> list = null; // remains NULL if array value is skipped
            Type itemType = null; // type of array items

            if( type != null ) {
                if( type instanceof ParameterizedType ) {
                    ParameterizedType pt = (ParameterizedType)type;
                    type = pt.getRawType();
                    if( type == List.class ) {
                        list = new ArrayList<>();
                        Type[] argTypes = pt.getActualTypeArguments();
                        itemType = argTypes != null && argTypes.length == 1 ? argTypes[0] : Object.class;
                    }
                } else if( type == List.class || type == Object.class ) {
                    list = new ArrayList<>();
                    itemType = Object.class;
                } else if( ((Class<?>)type).isArray() ) {
                    arr = true;
                    list = new ArrayList<>();
                    itemType = ((Class<?>)type).getComponentType();
                }
            } else {
                list = new ArrayList<>();
            }

            skipWhitespaces();
            if( nextChar() != ']' ) {
                bufPos--;
                while( true ) {
                    Object value = parse(itemType);
                    if( list != null ) {
                        list.add(value);
                    }

                    skipWhitespaces();

                    char ch = nextChar();
                    if( ch == ',' ) {
                        skipWhitespaces();
                        if( nextChar() == ']' )
                            break;

                        bufPos--;
                        continue;
                    }

                    if( ch == ']' )
                        break;

                    throw new IllegalArgumentException("Invalid JSON array, expected ',' or ']' at " + bufPos + ": " + getCurrentSubstring());
                }
            }

            if( arr ) {
                int size = list.size();
                Object resultArr = Array.newInstance((Class<?>)itemType, size);
                if( ((Class<?>)itemType).isPrimitive() ) {
                    for( int i = 0; i < size; i++ ) {
                        Array.set(resultArr, i, list.get(i));
                    }
                    return resultArr;
                } else {
                    return list.toArray((Object[])resultArr);
                }
            } else {
                return list;
            }
        } catch( Exception e ) {
            e.printStackTrace();
            return null;
        }
    }

    private Object parseObject(Type type) {
        try {
            Descriptor d = Descriptor.get(type);
            Object obj = d.newInstance();

            skipWhitespaces();
            if( nextChar() != '}' ) {
                bufPos--;
                Map<String, Object> inline = d.inline != null ? (Map<String, Object>)d.inline.get(obj) : null;
                while( true ) {
                    if( obj != null ) {
                        String name = parseString();
                        skipWhitespaces();
                        if( nextChar() != ':' ) {
                            throw new IllegalArgumentException("Invalid JSON object, expected ':' at " + bufPos + ": " + getCurrentSubstring());
                        }

                        if( d.map ) {
                            Object value = parse(d.valType);
                            ((Map)obj).put(name, value);
                        } else {
                            Object value = parse(d.types.get(name));
                            Field f = d.fieldMap.get(name);
                            if( f != null ) {
                                try {
                                    f.set(obj, value);
                                } catch( Exception e ) {
                                    System.err.println(e.toString());
                                }
                            } else if( inline != null ) {
                                inline.put(name, value);
                            } else {
                                if( DUMP_FIELD_NOT_FOUND )
                                    System.err.println("Field '" + name + "' not found in class '" + type + "'");
//                                    System.err.println("Field '" + name + "' in class '" + type + "' is final and cannot be updated");
                            }
                        }
                    } else {
                        // SKIP OBJECT
                        parse((Type)null);
                        skipWhitespaces();
                        if( nextChar() != ':' )
                            throw new IllegalArgumentException("Invalid JSON object, expected ':' at " + bufPos + ": " + getCurrentSubstring());
                        parse((Type)null);
                    }

                    skipWhitespaces();
                    char ch = nextChar();
                    if( ch == ',' ) {
                        skipWhitespaces();
                        if( nextChar() == '}' )
                            break;

                        bufPos--;
                        continue;
                    }
                    if( ch == '}' )
                        break;

                    throw new IllegalArgumentException("Invalid JSON object, expected ',' at " + bufPos + ": " + getCurrentSubstring());
                }
            }
            return obj;
        } catch( Exception e ) {
            if( e instanceof IllegalArgumentException )
                throw (IllegalArgumentException)e;
            e.printStackTrace();
            return null;
        }
    }

    private String parseString() {
        char quote = nextChar();
        if( quote != '"' && quote != '\'' )
            throw new IllegalArgumentException("Invalid JSON string at " + bufPos + ": " + getCurrentSubstring());

        // TODO: rework it too! The bug with static chwr;
//        CharBuffer writer = chwr;
        CharBuffer writer = new CharBuffer();
        writer.reset();
        mark = bufPos + 1;
        while( true ) {
            try {
                char ch = buf[++bufPos];
                if( ch == quote ) {
                    writer.append(buf, mark, bufPos);
                    mark = -1;
                    break;
                }
                if( ch == '\\' ) {
                    writer.append(buf, mark, bufPos);
                    mark = bufPos + 1;
                    ch = nextChar();
                    switch( ch ) {
                        case 't':
                            writer.append('\t');
                            mark++;
                            break;
                        case 'r':
                            writer.append('\r');
                            mark++;
                            break;
                        case 'n':
                            writer.append('\n');
                            mark++;
                            break;
                        case 'u':
                            int code = Integer.parseInt(next(4), 16);
                            writer.append(code);
                            mark = bufPos + 1;
                            break;
                        default:
                            writer.append(ch);
                            mark++;
                            break;
                    }
                }
            } catch( ArrayIndexOutOfBoundsException ignored ) {
                writer.append(buf, mark, bufPos);
                if( nextPos >= strLen ) {
                    throw new IllegalArgumentException("Invalid JSON string at " + bufPos + ": " + getCurrentSubstring());
                }
                mark = -1;
                fetch();
                mark = bufPos + 1;
            }
        }
        return writer.toString();
    }

//    private static CharBuffer chwr = new CharBuffer();
//    private static CharBuffer chwr;

    public static String stringify(Object obj) {
        return stringify(obj, null);
    }

    public static String stringify(Object obj, String scope) {
        if( obj == null )
            return "null";
        JSON J = new JSON();
        J.scope = scope == null ? Descriptor.DEFAULT_SCOPE : scope;

        // TODO: rework it! The fucking bug with static chwr!
//        chwr.reset();
        CharBuffer chwr = new CharBuffer();
        return J.stringify(chwr, obj, obj.getClass()).toString();
    }

    private static final String[] esc = new String[128];

    static {
        esc['\\'] = "\\\\";
        esc['"'] = "\\\"";
        esc['\r'] = "\\r";
        esc['\n'] = "\\n";
        esc['\t'] = "\\t";
    }

    private static final char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    private static final char[] hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static void integerSerializer(JSON json, CharBuffer writer, Object val) {
        writeInt(writer, (int)val);
    }

    private static void atomicIntegerSerializer(JSON json, CharBuffer writer, Object val) {
        writeInt(writer, ((AtomicInteger)val).get());
    }

    private static void longSerializer(JSON json, CharBuffer writer, Object val) {
        writeLong(writer, (long)val);
    }

    private static void doubleSerializer(JSON json, CharBuffer writer, Object val) {
        writer.append(String.valueOf(val));
    }

    private static void writeInt(CharBuffer writer, int val) {
        if( val < 0 ) {
            writer.append('-');
            val = -val;
        }
        if( val > 9 ) {
            // TODO: optimize
            StringBuilder sb = new StringBuilder();
            while( val > 9 ) {
                sb.insert(0, digits[val % 10]);
                val /= 10;
            }
            writer.append(digits[val]);
            writer.append(sb);
        } else {
            writer.append(digits[val]);
        }
    }

    private static void writeLong(CharBuffer writer, long val) {
        if( val < 0 ) {
            writer.append('-');
            val = -val;
        }
        if( val > 9 ) {
            // TODO: optimize
            StringBuilder sb = new StringBuilder();
            while( val > 9 ) {
                sb.insert(0, digits[(int)(val % 10)]);
                val /= 10;
            }
            writer.append(digits[(int)val]);
            writer.append(sb);
        } else {
            writer.append(digits[(int)val]);
        }
    }

    private static void booleanSerializer(JSON json, CharBuffer writer, Object val) {
        writer.append((Boolean)val ? TRUE : FALSE);
    }

    private void nullSerializer(CharBuffer writer, Object val) {
        writer.append(NULL);
    }

    private static void dateSerializer(JSON json, CharBuffer writer, Object val) {
        writer.append('"').append(DF_OUT.format((Date)val)).append('"');
    }

    private void intArraySerializer(CharBuffer writer, Object val) {
        writer.append('[');
        int[] arr = (int[])val;
        if( arr.length > 0 ) {
            for( int value : arr ) {
                writeInt(writer, value);
                writer.append(',');
            }
            writer.back(1);
        }
        writer.append(']');
    }

    private void longArraySerializer(CharBuffer writer, Object val) {
        writer.append('[');
        long[] arr = (long[])val;
        if( arr.length > 0 ) {
            for( long value : arr ) {
                writeLong(writer, value);
                writer.append(',');
            }
            writer.back(1);
        }
        writer.append(']');
    }

    private static void writeHexByte(CharBuffer writer, int val) {
        writer.append(hex[(val >> 4) & 0xf]);
        writer.append(hex[val & 0xf]);
    }

    private void byteArraySerializer(CharBuffer writer, Object val) {
        writer.append('"');
        byte[] bytes = (byte[])val;
        for( byte b : bytes ) {
            writeHexByte(writer, b);
        }
        writer.append('"');
    }

    private static void stringSerializer(JSON json, CharBuffer writer, Object val) {
        writer.append('"');

        String str = val.toString();
        int len = str.length();
//        str.getChars(0, len, buf, 0);
        int s = 0;
        for( int i = 0; i < len; i++ ) {
//            char ch = buf[i];
            char ch = str.charAt(i);
            if( ch < 128 && esc[ch] != null ) {
                if( s < i ) {
                    writer.append(str, s, i);
                }
                writer.append(esc[ch]);
                s = i + 1;
            }
        }
        if( s < len ) {
            writer.append(str, s, len);
        }

        writer.append('"');
    }


    private static final Map<Type, Serializer> serializerMap;

    static {
        serializerMap = new ConcurrentHashMap<>();
        serializerMap.put(String.class, JSON::stringSerializer);
        serializerMap.put(Character.class, JSON::stringSerializer);
        serializerMap.put(int.class, JSON::integerSerializer);
        serializerMap.put(Integer.class, JSON::integerSerializer);
        serializerMap.put(AtomicInteger.class, JSON::atomicIntegerSerializer);
        serializerMap.put(long.class, JSON::longSerializer);
        serializerMap.put(Long.class, JSON::longSerializer);
        serializerMap.put(float.class, JSON::doubleSerializer);
        serializerMap.put(Float.class, JSON::doubleSerializer);
        serializerMap.put(double.class, JSON::doubleSerializer);
        serializerMap.put(Double.class, JSON::doubleSerializer);
        serializerMap.put(boolean.class, JSON::booleanSerializer);
        serializerMap.put(Boolean.class, JSON::booleanSerializer);
        serializerMap.put(Date.class, JSON::dateSerializer);
        serializerMap.put(Path.class, JSON::stringSerializer);
    }

    public interface Serializer {
        void toJson(JSON json, CharBuffer writer, Object val);
    }

    private static void unknownSerializer(JSON json, CharBuffer writer, Object val) {
        json.stringify(writer, val, val.getClass());
    }


    CharBuffer stringify(CharBuffer writer, Object obj, Type type) {
        if( obj == null ) {
            return writer.append(NULL);
        }
        getSerializer(type).toJson(this, writer, obj);
        return writer;
    }

    static Serializer getSerializer(Type t) {
        Serializer ser = serializerMap.get(t);
        if( ser == null ) {
            ser = createSerializer(t);
            serializerMap.put(t, ser);
        }
        return ser;
    }

    private void collectionSerializer(CharBuffer writer, Object val) {
        writer.append('[');
        Collection<?> coll = (Collection<?>)val;
        if( !coll.isEmpty() ) {
            for( Object item : coll ) {
                stringify(writer, item, item.getClass());
                writer.append(',');
            }
            writer.back(1);
        }
        writer.append(']');
    }

    private static Serializer createSerializer(Type t) {
        if( t == Object.class ) {
            return JSON::unknownSerializer;
        }
        Class<?> cls = (Class<?>)(t instanceof ParameterizedType ? ((ParameterizedType)t).getRawType() : t);
        if( Map.class.isAssignableFrom(cls) ) {
            return createMapSerializer(t);
        }
        if( Collection.class.isAssignableFrom(cls) ) {
            return createCollectionSerializer(t);
        }

        if( cls.isArray() ) {
            return createArraySerialization(cls);
        }

        try {
            Method toJson = cls.getDeclaredMethod("toJson", CharBuffer.class);
            return (json, writer, val) -> {
                try {
                    toJson.invoke(val, writer);
                } catch( Exception e ) {
                    throw new RuntimeException(e);
                }
            };
        } catch( NoSuchMethodException ignored ) {
        }

        try {
            Method toJson = cls.getDeclaredMethod("toJson");
            if( toJson.getReturnType() == String.class ) {
                return (json, writer, val) -> {
                    try {
                        writer.append(toJson.invoke(val));
                    } catch( Exception e2 ) {
                        throw new RuntimeException(e2);
                    }
                };
            }
        } catch( NoSuchMethodException ignore ) {
        }

        Descriptor d = Descriptor.get(t);
        return d::toJson;
    }

    private static Serializer createArraySerialization(Class<?> cls) {
        Class<?> compType = cls.getComponentType();
        if( compType.isPrimitive() ) {
            if( compType == byte.class ) {
                return JSON::byteArraySerializer;
            } else if( compType == int.class ) {
                return JSON::intArraySerializer;
            } else if( compType == long.class ) {
                return JSON::longArraySerializer;
            }
        }

        Serializer valSer = getSerializer(compType);
        return (json, writer, val) -> {
            writer.append('[');
            Object[] arr = (Object[])val;
            if( arr.length > 0 ) {
                for( Object item : arr ) {
                    if( item == null ) {
                        writer.append(NULL);
                    } else {
                        valSer.toJson(json, writer, item);
                    }
//                        stringify(writer, item, item.getClass());
                    writer.append(',');
                }
                writer.back(1);
            }
            writer.append(']');
        };
    }

    private static Serializer createCollectionSerializer(Type type) {
        Serializer valSer;
        if( type instanceof ParameterizedType ) {
            ParameterizedType pType = (ParameterizedType)type;
            Type[] aTypes = pType.getActualTypeArguments();
            if( aTypes != null && aTypes.length == 1 ) {
                valSer = getSerializer(aTypes[0]);
            } else {
                valSer = JSON::unknownSerializer;
            }
        } else {
            valSer = JSON::unknownSerializer;
        }
        return (json, writer, val) -> {
            writer.append('[');
            Collection<?> coll = (Collection<?>)val;
            if( !coll.isEmpty() ) {
                for( Object item : coll ) {
                    if( item == null ) {
                        writer.append(NULL);
                    } else {
                        valSer.toJson(json, writer, item);
                    }
//                    stringify(writer, item, item.getClass());
                    writer.append(',');
                }
                writer.back(1);
            }
            writer.append(']');
        };
    }

    private static Serializer createMapSerializer(Type type) {
        Serializer valSer;
        if( type instanceof ParameterizedType ) {
            ParameterizedType pType = (ParameterizedType)type;
            Type[] aTypes = pType.getActualTypeArguments();
            if( aTypes != null && aTypes.length == 2 ) {
                valSer = getSerializer(aTypes[1]);
            } else {
                valSer = JSON::unknownSerializer;
            }
        } else {
            valSer = JSON::unknownSerializer;
        }
        return (json, writer, val) -> {
            writer.append('{');
            Map<?, ?> map = (Map<?, ?>)val;
            if( !map.isEmpty() ) {
                for( Map.Entry<?, ?> entry : map.entrySet() ) {
                    Object value = entry.getValue();
                    if( value != null ) {
                        writer.append('"').append(entry.getKey()).append(QUOTE_COLUMN);
                        valSer.toJson(json, writer, value);
//                        stringify(writer, value, value.getClass());
                        writer.append(',');
                    }
                }
                writer.back(1);
            }
            writer.append('}');
        };
    }

    private String getCurrentSubstring() {
        int pos = bufPos < strLen ? bufPos : 0;
        return str.substring(pos, min(bufPos + 40, strLen));
    }

    @Inherited
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Inherit {
    }

    @Inherited
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Inline {
    }

    @Inherited
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Hidden {
        String[] value() default "";
    }

    @Inherited
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Scope {
        String[] value() default "";
    }


}
