package ru.xeloo.xml;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.xeloo.io.CharBuffer;

public class XMLParser {

    private static final Map<String, String> entityMap = Map.of(
        "&quot;", "\"",
        "&amp;", "&",
        "&apos;", "'",
        "&lt;", "<",
        "&gt;", ">"
    );

    private static final char EOS = (char)-1;

    private static final int BUFF_SIZE = 4000;
    //    private static final int BUFF_SIZE = 10;
    private final char[] buf = new char[BUFF_SIZE];
    private static final int MAX_ENTITY_SIZE = 10;
    private int strLen;
    private int strPos; // string position of buffer begin;
    private int nextPos; // string position of next buffer fetch;
    private int bufPos; // position of current char
    private int mark;

    private String str;

    private CharBuffer chwr = new CharBuffer();

    public XMLParser(String str) {
        this(str, 0);
    }

    public XMLParser(String str, int pos) {
        if( str.charAt(pos) == '\ufeff' ) {
            pos++;
        }
        setReadBuffer(str, pos);
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
            mark = copyTo;
            bufPos = mark + copyLen;
        } else {
            bufPos = copyTo;
        }

        str.getChars(nextPos, endPos, buf, bufPos);
        strPos = nextPos - bufPos;
        nextPos = endPos;
        bufPos--;
    }

    private void skipWhitespaces() {
        while( true ) {
            try {
                char ch = buf[++bufPos];
                if( ch == '<' ) {
                    if( checkValue('<', '!', '-', '-') ) {
                        skipComment();
                    } else {
                        bufPos--;
                        break;
                    }
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
        while( true ) {
            char ch = nextChar();
            if( ch == '-' ) {
                if( checkValue('-', '-', '>') )
                    break;
            }
        }
    }

    private boolean checkValue(char... test) {
        int dPos = mark == -1 ? -1 : bufPos - mark;
        if( mark == -1 )
            mark = bufPos;
        bufPos--;
        for( char ch : test ) {
            if( ch != nextChar() ) {
                bufPos = mark;
                if( dPos == -1 ) {
                    mark = -1;
                } else {
                    bufPos += dPos;
                }
                return false;
            }
        }
        if( dPos == -1 )
            mark = -1;
        return true;
    }


    private static class XMLHeader {
        String version;
        String encoding;
    }

    public Object parse(Type type) {
        skipWhitespaces();
        if( nextChar() != '<' ) {
            throw new IllegalArgumentException("Invalid XML at " + bufPos + ": " + getCurrentSubstring());
        }

        if( nextChar() == '?' ) {
            XML.Name tag = parseName();
            XMLHeader h = (XMLHeader)parseTag(tag, XMLHeader.class);
            skipWhitespaces();
            if( nextChar() != '<' ) {
                throw new IllegalArgumentException("Invalid XML at " + bufPos + ": " + getCurrentSubstring());
            }
        } else {
            bufPos--;
        }

        XML.Name tag = parseName();
        return parseTag(tag, type);
    }

    static final Set<Type> simpleTypes = new HashSet<>(Set.of(
        String.class,
        int.class,
        Integer.class,
        long.class,
        Long.class,
        boolean.class,
        Boolean.class,
        Date.class
    ));

    private Object parseTag(XML.Name thisTag, Type type) {
        try {
            Descriptor d = null;
            Object obj = null;

            if( type != null && !simpleTypes.contains(type) ) {
                if( ((Class<?>)type).isEnum() ) {
                    simpleTypes.add(type);
                } else {
                    d = Descriptor.get(type);
                    if( d.tag != null && !thisTag.equals(d.tag) ) {
                        throw new IllegalArgumentException("Invalid tag name '" + thisTag + "', expected '" + d.tag + "' at " + bufPos + ": " + getCurrentSubstring());
                    }

                    obj = d.newInstance();
                }
            }

            boolean end = false;

            while( true ) {
                skipWhitespaces();
                char ch = nextChar();
                if( ch == '>' ) {
                    break;
                }
                if( ch == '/' || ch == '?' ) {
                    ch = nextChar();
                    if( ch == '>' ) {
                        end = true;
                        break;
                    }
                    throw new IllegalArgumentException("Invalid tag at " + bufPos + ": " + getCurrentSubstring());
                }
                bufPos--;

                XML.Name attr = parseName();
                skipWhitespaces();
                if( nextChar() != '=' ) {
                    throw new IllegalArgumentException("Invalid attr definition '" + attr + "' at " + bufPos + ": " + getCurrentSubstring());
                }
                skipWhitespaces();

                if( obj != null ) {
                    if( d.map ) {
                        Object value = parseAttr(d.valType);
                        ((Map)obj).put(attr.name, value);
                    } else {
                        Field f = d.fieldMap.get(attr.name);
                        if( f != null ) {
                            Object value = parseAttr(d.types.get(attr.name));
                            f.set(obj, value);
                        } else {
                            parseString();
                            if( XML.DUMP_FIELD_NOT_FOUND )
                                System.err.println("Field '" + attr + "' not found in class '" + type + "'");
                            //                                    System.err.println("Field '" + name + "' in class '" + type + "' is final and cannot be updated");
                        }
                    }
                } else {
                    parseString();
                }
            }

            if( !end ) {
                if( obj == null ) {
                    return parseTagContent(thisTag, type, null);
                }
                while( true ) {
                    skipWhitespaces();
                    char ch = nextChar();
                    if( ch != '<' ) {
                        // PARSE TEXT
                        bufPos--;
                        if( d.inline != null ) {
                            d.inline.set(obj, parseTagContent(thisTag, d.inline.getGenericType(), obj));
                            return obj;
                        }
                        return parseTagContent(thisTag, type, obj);
//                        throw new IllegalArgumentException("Invalid XML at " + bufPos + ": " + getCurrentSubstring());
                    }

                    // TODO: PARSE CDATA

                    ch = nextChar();
                    end = ch == '/';
                    if( !end ) {
                        bufPos--;
                    }

                    XML.Name tag = parseName();

                    if( end ) {
                        if( !thisTag.equals(tag) ) {
                            throw new IllegalArgumentException("Invalid closing tag name '" + tag + "', expected '" + thisTag + "' at " + bufPos + ": " + getCurrentSubstring());
                        }
                        skipWhitespaces();
                        if( nextChar() != '>' ) {
                            throw new IllegalArgumentException("Invalid closing tag '" + tag + "' at " + bufPos + ": " + getCurrentSubstring());
                        }
                        break;
                    }

                    if( d.list ) {
                        Object value = parseTag(tag, d.valType);
                        ((Collection)obj).add(value);
                    } else {
                        Field f = d.fieldMap.get(tag.name);
                        if( f != null ) {
                            Type fType = d.types.get(tag.name);
                            if( fType instanceof ParameterizedType && Collection.class.isAssignableFrom((Class<?>)((ParameterizedType)fType).getRawType()) ) {
                                Type itemType = ((ParameterizedType)fType).getActualTypeArguments()[0];
                                Collection col = (Collection)f.get(obj);
                                if( col == null ) {
                                    try {
                                        Type rawType = ((ParameterizedType)fType).getRawType();
                                        if( rawType == List.class ) {
                                            col = new ArrayList();
                                        }
                                        f.set(obj, col);
                                    } catch( Exception e ) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                boolean inline = f.getAnnotation(XML.Inline.class) != null;
                                if( inline ) {
                                    Object value = parseTag(tag, itemType);
                                    col.add(value);
                                } else {
                                    // now expect tag without attrs
                                    skipWhitespaces();
                                    if( nextChar() != '>' ) {
                                        throw new RuntimeException("Unexpected attributes");
                                    }

                                    XML.List $list = f.getAnnotation(XML.List.class);
                                    String itemTag;
                                    if( $list != null ) {
                                        itemTag = $list.item();
                                    } else {
                                        Descriptor itemD = Descriptor.get(itemType);
                                        itemTag = itemD.tag != null ? itemD.tag.name : itemD.defTag.name;
                                    }
                                    while( true ) {
                                        skipWhitespaces();
                                        if( nextChar() != '<' ) {
                                            throw new RuntimeException("Unexpected content");
                                        }

                                        end = nextChar() == '/';
                                        if( !end ) {
                                            bufPos--;
                                        }

                                        XML.Name _tag = parseName();

                                        if( end ) {
                                            if( !tag.equals(_tag) ) {
                                                throw new IllegalArgumentException("Invalid closing tag name '" + _tag + "', expected '" + tag + "' at " + bufPos + ": " + getCurrentSubstring());
                                            }
                                            skipWhitespaces();
                                            if( nextChar() != '>' ) {
                                                throw new IllegalArgumentException("Invalid closing tag '" + _tag + "', expected '" + tag + "' at " + bufPos + ": " + getCurrentSubstring());
                                            }
                                            break;
                                        }

                                        if( _tag.name.equals(itemTag) ) {
                                            Object value = parseTag(_tag, itemType);
                                            col.add(value);
                                        } else {
                                            // skip tags that not match
                                            parseTag(_tag, null);
                                        }
                                    }
                                }
                            } else {
                                Object value = parseTag(tag, fType);
                                f.set(obj, value);
                            }
                        } else {
                            parseTag(tag, null);
                            if( XML.DUMP_FIELD_NOT_FOUND )
                                System.err.println("Field '" + tag.name + "' not found in class '" + type + "'");
                            //                                    System.err.println("Field '" + name + "' in class '" + type + "' is final and cannot be updated");
                        }
                    }

                }
            }

            return obj != null ? obj : type == String.class ? "" : null;
        } catch( Exception e ) {
            if( e instanceof IllegalArgumentException )
                throw (IllegalArgumentException)e;
            e.printStackTrace();
            return null;
        }
    }

    private Object parseTagContent(XML.Name thisTag, Type type, Object obj) {
        if( type == null || type == String.class ) {
            return parseString("</" + thisTag + ">");
        }
        if( type == Date.class ) {
            return parseDate(parseString("</" + thisTag + ">"));
        }
        Class cls = (Class)type;
        if( cls.isEnum() ) {
            return EnumValues.getValue(cls, parseString("</" + thisTag + ">"));
        }
        try {
            if( type == Integer.TYPE || type == Integer.class )
                return parseInteger();
            if( type == Long.TYPE || type == Long.class )
                return parseLong();
            if( type == Double.TYPE || type == Double.class )
                return parseDouble();
            if( type == Boolean.TYPE || type == Boolean.class )
                return parseBoolean();
            if( type == Character.TYPE || type == Character.class )
                return parseChar();
            if( cls.getComponentType() == byte.class ) {
                return parseByteArray();
            }
        } finally {
            bufPos++;
            if( !checkValue(("</" + thisTag + ">").toCharArray()) ) {
                throw new RuntimeException("Invalid closing tag name, expected '" + thisTag + "' at " + +bufPos + ": " + getCurrentSubstring());
            }
        }
        return parseString("</" + thisTag + ">");
    }

    private XML.Name parseName() {
        String ns = null;
        String name = parseNameStr();
        if( nextChar() == ':' ) {
            ns = name;
            name = parseNameStr();
        } else {
            bufPos--;
        }

        if( ns != null && ns.isEmpty() || name.isEmpty() ) {
            throw new IllegalArgumentException("Invalid name '" + (ns != null ? ns + ":" : "") + name + "' at " + bufPos + ": " + getCurrentSubstring());
        }

        return new XML.Name(ns, name);
    }

    private String parseNameStr() {
        CharBuffer writer = chwr;
        writer.reset();

        while( true ) {
            char ch = nextChar();
            if( ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '-' || ch == '_' ) {
                writer.append(ch);
            } else {
                bufPos--;
                break;
            }
        }
        return writer.toString();
    }

    private Object parseAttr(Type type) {
        if( type == null || type == String.class )
            return parseString();
        if( type == Integer.TYPE || type == Integer.class )
            return parseInteger();
        if( type == Long.TYPE || type == Long.class )
            return parseLong();
        if( type == Double.TYPE || type == Double.class )
            return parseDouble();
        if( type == Boolean.TYPE || type == Boolean.class )
            return parseBoolean();
        if( type == Character.TYPE || type == Character.class )
            return parseChar();
        if( type == Date.class ) {
            return parseDate(parseString());
        }
        Class cls = (Class)type;
        if( cls.isEnum() ) {
            return EnumValues.getValue(cls, parseString());
        }
        if( cls.getComponentType() == byte.class ) {
            return parseByteArray();
        }
        return parseString();
    }

    private String parseString() {
        return parseString(null);
    }

    private String parseString(String terminator) {
        char[] tChars = terminator != null ? terminator.toCharArray() : null;
        char quote = terminator == null ? nextChar() : '<';
        if( quote != '"' && quote != '\'' && quote != '<' )
            throw new IllegalArgumentException("Invalid string at " + bufPos + ": " + getCurrentSubstring());

        CharBuffer writer = chwr;
        writer.reset();
        mark = bufPos + 1;
//        if( terminator == null )
//            mark++;
        while( true ) {
            try {
                char ch = buf[++bufPos];
                if( ch == quote ) {
                    writer.append(buf, mark, bufPos);
                    // TODO: verify if terminator is correct on the edge

                    if( ch != '<' ) {
                        mark = -1;
                        break;
                    } else {
                        mark = bufPos;
                        if( checkValue(tChars) ) {
                            mark = -1;
                            break;
                        }
                    }
                }
                if( ch == '&' ) {
                    writer.append(buf, mark, bufPos);
                    mark = bufPos;
                    int remains = MAX_ENTITY_SIZE;
                    String entity;
                    while( true ) {
                        ch = nextChar();
                        if( ch == ';' ) {
                            entity = new String(buf, mark, bufPos - mark + 1);
                            String s = entityMap.get(entity);
                            writer.append(s == null ? entity : s);
                            mark = bufPos + 1;
                            break;
                        }

                        if( --remains == 0 ) {
                            break;
                        }
                    }
                }
            } catch( ArrayIndexOutOfBoundsException ignored ) {
                writer.append(buf, mark, bufPos);
                if( nextPos >= strLen ) {
                    throw new IllegalArgumentException("Invalid string at " + bufPos + ": " + getCurrentSubstring());
                }
                mark = -1;
                fetch();
                mark = bufPos + 1;
            }
        }
        return writer.toString();
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
                throw new IllegalArgumentException("Invalid integer at " + mark + ": " + getCurrentSubstring());
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
                throw new IllegalArgumentException("Invalid number at " + mark + ": " + getCurrentSubstring());
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
                    throw new IllegalArgumentException("Invalid number at " + mark + ": " + getCurrentSubstring());
                dot = true;
            } else if( ch == 'E' || ch == 'e' ) {
                if( exp )
                    throw new IllegalArgumentException("Invalid number at " + mark + ": " + getCurrentSubstring());
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

    private Object parseNumber(Type type) {
//        pos--;
        if( type != null ) {
            if( type == Integer.TYPE || type == Integer.class )
                return parseInteger();
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
        if( checkValue(XML.TRUE) ) {
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
            throw new IllegalArgumentException("Invalid value at " + bufPos + ": " + getCurrentSubstring());
        }
    }

    private Object parseFalse(Type type) {
        if( checkValue(XML.FALSE) ) {
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
            throw new IllegalArgumentException("Invalid value at " + bufPos + ": " + getCurrentSubstring());
        }
    }

    private Object parseNull(Type type) {
        if( checkValue(XML.NULL) ) {
            return null;
        } else {
            throw new IllegalArgumentException("Invalid value at " + bufPos + ": " + getCurrentSubstring());
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

    private Date parseDate(String str) {
        try {
            return XML.DF.parse(str);
        } catch( ParseException ignored ) {
        }
        for( DateFormat df : XML.DF_ALT ) {
            try {
                return df.parse(str);
            } catch( ParseException ignored ) {
            }
        }
        return null;
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
            if( !checkValue(XML.FALSE) ) {
                throw new IllegalArgumentException("Invalid value at " + mark + ": " + getCurrentSubstring());
            }
            value = false;
        } else if( ch == 't' ) {
            if( !checkValue(XML.TRUE) ) {
                throw new IllegalArgumentException("Invalid value at " + mark + ": " + getCurrentSubstring());
            }
            value = true;
        } else if( ch == '1' ) {
            value = true;
        } else if( ch == '0' ) {
            value = true;
        } else {
            throw new IllegalArgumentException("Invalid value at " + mark + ": " + getCurrentSubstring());
        }

        if( quote != 0 ) {
            ch = nextChar();
            if( ch != quote ) {
                throw new IllegalArgumentException("Invalid value at " + mark + ": " + getCurrentSubstring());
            }
        }

        return value;
    }


    private String getCurrentSubstring() {
        String s = new String(buf);
        return s.substring(bufPos, Math.min(bufPos + 40, BUFF_SIZE));
    }

}
