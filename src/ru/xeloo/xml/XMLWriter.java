package ru.xeloo.xml;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.xeloo.io.CharBuffer;

public class XMLWriter {

    private static String[] esc = new String[128];

    static {
        esc['"'] = "&quot;";
        esc['&'] = "&amp;";
        esc['\''] = "&apos;";
        esc['<'] = "&lt;";
        esc['>'] = "&gt;";
    }

    private static char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
    private static char[] hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private final CharBuffer buffer = new CharBuffer();

    private int indent = 0;

    private static final Map<Type, Serializer> serializerMap;

    static {
        serializerMap = new HashMap<>();
        serializerMap.put(String.class, XMLWriter::stringSerializer);
        serializerMap.put(Character.class, XMLWriter::charSerializer);
        serializerMap.put(int.class, XMLWriter::integerSerializer);
        serializerMap.put(Integer.class, XMLWriter::integerSerializer);
        serializerMap.put(long.class, XMLWriter::longSerializer);
        serializerMap.put(Long.class, XMLWriter::longSerializer);
        serializerMap.put(boolean.class, XMLWriter::booleanSerializer);
        serializerMap.put(Boolean.class, XMLWriter::booleanSerializer);
        serializerMap.put(Date.class, XMLWriter::dateSerializer);
    }

    private String encoding;

    public XMLWriter() {
    }

    public XMLWriter(String encoding) {
        this.encoding = encoding;
    }

    public interface Serializer {
        void toXML(XMLWriter writer, Object val, XML.Name tag);
    }

    private void emptyTag(XML.Name tag) {
        if( tag != null ) {
            for( int i = 0; i < indent; i++ ) {
                buffer.append("  ");
            }
            buffer.append('<').append(tag).append("/>\n");
        }
    }

    private void startTag(XML.Name tag) {
        if( tag != null ) {
            for( int i = 0; i < indent; i++ ) {
                buffer.append("  ");
            }
            buffer.append('<').append(tag).append('>');
        }
    }

    private void endTag(XML.Name tag) {
        if( tag != null ) {
            buffer.append("</").append(tag).append(">\n");
        }
    }

    private void unknownSerializer(Object val) {
//        serialize(val);
    }

    private void stringSerializer(Object val, XML.Name tag) {
        if( val == null || ((String)val).isEmpty() ) {
            emptyTag(tag);
            return;
        }

        CharBuffer buf = buffer;
        startTag(tag);
        String str = String.valueOf(val);
        int len = str.length();
//        str.getChars(0, len, buf, 0);
        int s = 0;
        for( int i = 0; i < len; i++ ) {
//            char ch = buf[i];
            char ch = str.charAt(i);
            if( ch < 128 && esc[ch] != null ) {
                if( s < i ) {
                    buf.append(str, s, i);
                }
                buf.append(esc[ch]);
                s = i + 1;
            }
        }
        if( s < len ) {
            buf.append(str, s, len);
        }
        endTag(tag);
    }

    private void charSerializer(Object val, XML.Name tag) {
        startTag(tag);
        buffer.append((char)val);
        endTag(tag);
    }

    private void integerSerializer(Object val, XML.Name tag) {
        startTag(tag);
        writeInt((int)val);
        endTag(tag);
    }

    private void longSerializer(Object val, XML.Name tag) {
        startTag(tag);
        writeLong((long)val);
        endTag(tag);
    }

    private void writeInt(int val) {
        CharBuffer buf = buffer;
        if( val < 0 ) {
            buf.append('-');
            val = -val;
        }
        if( val > 9 ) {
            // TODO: optimize
            StringBuilder sb = new StringBuilder();
            while( val > 9 ) {
                sb.insert(0, digits[val % 10]);
                val /= 10;
            }
            buf.append(digits[val]);
            buf.append(sb);
        } else {
            buf.append(digits[val]);
        }
    }

    private void writeLong(long val) {
        CharBuffer buf = buffer;
        if( val < 0 ) {
            buf.append('-');
            val = -val;
        }
        if( val > 9 ) {
            // TODO: optimize
            StringBuilder sb = new StringBuilder();
            while( val > 9 ) {
                sb.insert(0, digits[(int)(val % 10)]);
                val /= 10;
            }
            buf.append(digits[(int)val]);
            buf.append(sb);
        } else {
            buf.append(digits[(int)val]);
        }
    }

    private void booleanSerializer(Object val, XML.Name tag) {
        startTag(tag);
        buffer.append((Boolean)val ? XML.TRUE : XML.FALSE);
        endTag(tag);
    }

    private void nullSerializer(Object val, XML.Name tag) {
        buffer.append(XML.NULL);
    }

    private void dateSerializer(Object val, XML.Name tag) {
        startTag(tag);
        buffer.append(XML.DF.format((Date)val));
        endTag(tag);
    }


    public String serialize(Object obj) {
        if( obj != null ) {
            buffer.append("<?xml version=\"1.0\" encoding=\"")
                  .append(encoding == null ? "utf-8" : encoding)
                  .append("\"?>").append('\n');
            getSerializer(null, obj.getClass()).toXML(this, obj, null);
        }
        return buffer.toString();
    }

    private static final Map<Field, Serializer> collSerializers = new HashMap<>();

    private static Serializer getSerializer(Field field, Type t) {
        Serializer ser = serializerMap.get(t);
        if( ser == null ) {
            ser = collSerializers.get(field);
            if( ser == null ) {
                Class<?> cls = (Class<?>)(t instanceof ParameterizedType ? ((ParameterizedType)t).getRawType() : t);
                if( Collection.class.isAssignableFrom(cls) ) {
                    ser = createCollectionSerializer(field, t);
                    collSerializers.put(field, ser);
                } else {
                    ser = createSerializer(t);
                    serializerMap.put(t, ser);
                }
            }
        }
        return ser;
    }

    private static Serializer createSerializer(Type t) {
        if( t == Object.class ) {
            return XMLWriter::stringSerializer;
        }
        Class<?> cls = (Class<?>)(t instanceof ParameterizedType ? ((ParameterizedType)t).getRawType() : t);
/*
        if( Map.class.isAssignableFrom(cls) ) {
            return createMapSerializer(t);
        }
*/
/*
        if( cls.isArray() ) {
            return createArraySerialization(cls);
        }
*/
        if( cls.isEnum() ) {
            return (writer, val, tag) -> {
                writer.startTag(tag);
                writer.buffer.append(EnumValues.toString((Enum<?>)val));
                writer.endTag(tag);
            };
        }

        return (writer, val, tag) -> writer.serializeObject(t, val, tag);
    }

    private String currentNS;
    private static final Map<Descriptor, Serializer[]> fieldSerializers = new HashMap<>();

    private void serializeObject(Type type, Object val, XML.Name tag) {
        Descriptor d = Descriptor.get(type);
        Serializer[] sers = fieldSerializers.computeIfAbsent(d, dd -> {
            Serializer[] arr = new Serializer[dd.children.size()];
            if( !dd.map && arr.length > 0 ) {
                for( int i = 0; i < arr.length; i++ ) {
                    Field field = dd.children.get(i);
                    arr[i] = getSerializer(field, field.getGenericType());
                }
            }
            return arr;
        });

        if( d.xTag != null )
            tag = d.xTag;
        else if( tag == null )
            tag = d.defTag;

        String oldNS = currentNS;
        if( d.ns != null )
            currentNS = d.ns;

        if( currentNS != null )
            tag = new XML.Name(currentNS, tag.name);

        CharBuffer buf = buffer;

        for( int i = 0; i < indent; i++ ) {
            buf.append("  ");
        }
        buf.append('<').append(tag);
        if( d.nsMap != null ) {
            for( Map.Entry<String, String> entry : d.nsMap.entrySet() ) {
                buf.append(" xmlns:").append(entry.getKey()).append("=\"").append(entry.getValue()).append('"');
            }
        }

        List<Field> attrs = d.attrs;
        for( int i = 0; i < attrs.size(); i++ ) {
            Field field = attrs.get(i);
            try {
                Object value = field.get(val);
                if( value != null ) {
                    XML.Name name = d.attrsName.get(i);
                    if( currentNS != null )
                        name = new XML.Name(currentNS, name.name);
                    buf.append(' ').append(name).append("=\"");
                    getSerializer(null, field.getGenericType()).toXML(this, value, null);
                    buf.append('"');
                }
            } catch( IllegalAccessException ignored ) {
            }
        }
        if( d.inline == null && d.children.isEmpty() ) {
            buf.append("/>\n");
        } else {
            buf.append('>');
            indent++;
            boolean empty = true;

            if( d.inline != null ) {
                try {
                    Object value = d.inline.get(val);
                    if( value != null ) {
                        empty = false;
                        getSerializer(null, d.inline.getGenericType()).toXML(this, value, null);
                    } else {
                        buf.append('\n');
                    }
                } catch( IllegalAccessException ignored ) {
                }
            } else {
                buf.append('\n');
                List<Field> fields = d.children;
                int len = fields.size();
                for( int i = 0; i < len; i++ ) {
                    try {
                        Field field = fields.get(i);
                        Object value = field.get(val);
                        if( value != null ) {
                            empty = false;
                            String cNS = d.childrenNS.get(i);
                            String _ns = currentNS;
                            if( cNS != null )
                                currentNS = cNS;
                            XML.Name name = d.childrenTag.get(i);
                            if( currentNS != null )
                                name = new XML.Name(currentNS, name.name);
                            sers[i].toXML(this, value, name);
                            currentNS = _ns;
                        }
                    } catch( Exception ignored ) {
                    }
                }
            }

            indent--;
            if( empty ) {
                buf.back(2).append("/>\n");
            } else {
                if( d.inline == null ) {
                    for( int i = 0; i < indent; i++ ) {
                        buf.append("  ");
                    }
                }
                buf.append("</").append(tag).append(">\n");
            }
        }

        currentNS = oldNS;
    }

    private static Serializer createCollectionSerializer(Field field, Type type) {
        Type itemType = null;
        Serializer valSer;
        if( type instanceof ParameterizedType ) {
            ParameterizedType pType = (ParameterizedType)type;
            Type[] aTypes = pType.getActualTypeArguments();
            if( aTypes.length == 1 ) {
                itemType = aTypes[0];
                valSer = getSerializer(null, itemType);
            } else {
                valSer = XMLWriter::stringSerializer;
            }
        } else {
            valSer = XMLWriter::stringSerializer;
        }

        boolean inline = field.getAnnotation(XML.Inline.class) != null;
        XML.List $list = field.getAnnotation(XML.List.class);
        String tagName;
        if( $list != null ) {
            tagName = $list.item();
        } else if( XMLParser.simpleTypes.contains(itemType) ) {
            tagName = "value";
        } else {
            Descriptor d = Descriptor.get(itemType);
            tagName = d.tag != null ? d.tag.name : field.getName();
        }
        XML.Name itemTag = new XML.Name(tagName);

        return (writer, val, tag) -> writer.serializeList(valSer, val, inline ? null : tag, itemTag);
    }

    private void serializeList(Serializer valSer, Object val, XML.Name tag, XML.Name itemTag) {
        CharBuffer buf = buffer;
        if( tag != null ) {
            startTag(tag);
            buf.append('\n');
            indent++;
        }
        Collection<?> coll = (Collection<?>)val;
        if( !coll.isEmpty() ) {
            for( Object item : coll ) {
                valSer.toXML(this, item, itemTag);
            }
            buf.back(1);
        }
        buf.append('\n');
        if( tag != null ) {
            indent--;
            for( int i = 0; i < indent; i++ ) {
                buf.append("  ");
            }
            endTag(tag);
        }
    }
}
