package ru.xeloo.xml;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.reflect.Modifier.STATIC;

class Descriptor {

    private final Type type;
    private Constructor<?> constructor;
    public boolean map;
    public boolean list;
    public Type keyType;
    public Type valType;
    public Map<String, Field> fieldMap;
    public Map<String, Type> types;
    public Field inline;

    public Map<String, String> nsMap;
    public String ns;
    public XML.Name tag;
    public XML.Name xTag;
    public XML.Name defTag;
    public XML.Name __itemTag;

    public List<Field> attrs = new ArrayList<>();
    public List<XML.Name> attrsName = new ArrayList<>();
    public List<Field> children = new ArrayList<>();
    public List<XML.Name> childrenTag = new ArrayList<>();
    public List<String> childrenNS = new ArrayList<>();

    private static final Map<Type, Descriptor> cache = new HashMap<>();

    public static Descriptor get(Type type) {
        Descriptor d = cache.get(type);
        if( d == null ) {
            d = new Descriptor(type);
            cache.put(type, d);
        }
        return d;
    }

    public Descriptor(Type type) {
        this.type = type;
        try {
            Class<?> objType;
            if( type instanceof ParameterizedType ) {
                ParameterizedType pType = (ParameterizedType)type;
                objType = (Class<?>)pType.getRawType();
                if( objType == Map.class ) {
                    objType = HashMap.class;
                } else if( objType == List.class ) {
                    objType = ArrayList.class;
                }
                if( Map.class.isAssignableFrom(objType) ) {
                    map = true;
                    Type[] aTypes = pType.getActualTypeArguments();
                    if( aTypes != null && aTypes.length == 2 ) {
                        keyType = aTypes[0];
                        valType = aTypes[1];
                    }
                }
                if( Collection.class.isAssignableFrom(objType) ) {
                    list = true;
                    Type[] aTypes = pType.getActualTypeArguments();
                    if( aTypes != null && aTypes.length == 1 ) {
                        valType = aTypes[0];
                    }
                }
            } else if( type == Map.class || type == Object.class || type == null ) {
                objType = HashMap.class;
                map = true;
                valType = Object.class;
            } else {
                objType = (Class<?>)type;
                fieldMap = new HashMap<>();
                types = new HashMap<>();

                XML.NS $ns = objType.getAnnotation(XML.NS.class);
                if( $ns != null ) {
                    String[] values = $ns.value();
                    if( values.length != 0 ) {
                        ns = values[0];
                        nsMap = new HashMap<>();
                        for( int i = 1; i < values.length; i += 2 ) {
                            nsMap.put(values[i - 1], values[i]);
                        }
                    }
                }

                defTag = new XML.Name(objType.getSimpleName());
                XML.Tag $tag = objType.getAnnotation(XML.Tag.class);
                if( $tag != null && !$tag.value().isEmpty() ) {
                    tag = new XML.Name($tag.value());
                    xTag = tag;
//                } else {
//                    xTag = defTag;
                }
//                if( xTag.ns == null && ns != null )
//                    xTag.ns = ns;

                findFields(objType);
            }
            if( !map ) {
                try {
                    constructor = objType.getDeclaredConstructor();
                    constructor.setAccessible(true);
                } catch( NoSuchMethodException ignored ) {
                }
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    private void findFields(Class<?> cls) {
        if( cls.getAnnotation(XML.Inherit.class) != null )
            findFields(cls.getSuperclass());

        for( Field field : cls.getDeclaredFields() ) {
            String name = field.getName();
            if( (field.getModifiers() & STATIC) != 0 || field.getAnnotation(XML.Hidden.class) != null )
                continue;

            field.setAccessible(true);
            String tagName = null;

            if( field.getAnnotation(XML.Attr.class) != null ) {
                attrs.add(field);
                XML.Attr $name = field.getAnnotation(XML.Attr.class);
                XML.Name tag;
                if( !$name.value().isEmpty() ) {
                    tag = new XML.Name($name.value());
                } else {
                    tag = new XML.Name(field.getName());
                }
//                            if( tag.ns == null && ns != null )
//                                tag.ns = ns;
                attrsName.add(tag);
            } else if( field.getAnnotation(XML.Inline.class) != null && !Collection.class.isAssignableFrom(field.getType()) ) {
                if( children.size() != 0 )
                    throw new IllegalArgumentException("Cannot use XML.Inline and regular children in " + type);
                if( inline != null )
                    throw new IllegalArgumentException("Only one filed marked XML.Inline allowed " + type);
                inline = field;
            } else {
                if( inline != null )
                    throw new IllegalArgumentException("Cannot use XML.Inline and regular children in " + type);
                children.add(field);
                XML.Tag $name = field.getAnnotation(XML.Tag.class);
                XML.Name tag;
                if( $name != null && !$name.value().isEmpty() ) {
                    String value = $name.value();
                    tag = new XML.Name(value);
                    tagName = value;
//                    fieldMap.put(value, field);
//                    types.put(value, field.getGenericType());
                } else {
                    tag = new XML.Name(field.getName());
                }
//                            if( tag.ns == null && ns != null )
//                                tag.ns = ns;
                childrenTag.add(tag);

                XML.NS $ns = field.getAnnotation(XML.NS.class);
                if( $ns != null && $ns.value().length == 1 ) {
                    childrenNS.add($ns.value()[0]);
                } else {
                    childrenNS.add(null);
                }

                XML.List $list = field.getAnnotation(XML.List.class);
                if( $list != null ) {
                    if( !Collection.class.isAssignableFrom(field.getType()) ) {
                        throw new IllegalArgumentException("XML.Item allowed for collections only");
                    }

                    if( $list.inline() || field.getAnnotation(XML.Inline.class) != null ) {
                        tagName = $list.item();
//                        fieldMap.put($list.item(), field);
//                        types.put($list.item(), field.getGenericType());
                    }

                    __itemTag = new XML.Name($list.item());
                }
            }

            if( tagName == null ) {
                tagName = name;
            }

            fieldMap.put(tagName, field);
            types.put(tagName, field.getGenericType());

            String up = tagName.substring(0, 1).toUpperCase();
            if( tagName.charAt(0) != up.charAt(0) ) {
                String upName = up + tagName.substring(1);
                fieldMap.put(upName, field);
                types.put(upName, field.getGenericType());
            }
        }
    }

    public Object newInstance() {
        try {
            if( map ) {
                return new HashMap<>();
            }
            if( constructor == null ) {
                throw new RuntimeException("Cannot create instance of " + type + ". Default constructor is not defined.");
            }
            return constructor.newInstance();
        } catch( Exception e ) {
            e.printStackTrace();
            return null;
        }
    }
}
