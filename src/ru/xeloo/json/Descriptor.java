package ru.xeloo.json;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ru.xeloo.io.CharBuffer;

import static java.lang.reflect.Modifier.STATIC;
import static ru.xeloo.json.JSON.QUOTE_COLUMN;

/**
 * Class descriptor used for fast serialization/deserialization of objects.
 */
class Descriptor {

    static final String DEFAULT_SCOPE = "default";

    private final Type type;
    private Constructor<?> constructor;
    public boolean map;
    public Type keyType;
    public Type valType;
    public Map<String, Field> fieldMap;
    public Map<String, Field[]> fields;
    public Map<String, Type> types;
    public Field inline;

    private static final Map<Type, Descriptor> cache = new ConcurrentHashMap<>();

    public static Descriptor get(Type type) {
        // TODO: REWORK IT !
        return cache.computeIfAbsent(type == null ? Void.TYPE : type, Descriptor::new);
    }

    private Descriptor(Type type) {
        this.type = type;
        try {
            Class<?> objType;
            if( type instanceof ParameterizedType ) {
                ParameterizedType pType = (ParameterizedType)type;
                objType = (Class<?>)pType.getRawType();
                if( objType == Map.class ) {
                    objType = HashMap.class;
                }
                if( Map.class.isAssignableFrom(objType) ) {
                    map = true;
                    Type[] aTypes = pType.getActualTypeArguments();
                    if( aTypes != null && aTypes.length == 2 ) {
                        keyType = aTypes[0];
                        valType = aTypes[1];
                    }
                }
            } else if( type == Map.class || type == Object.class || type == Void.TYPE ) {
                objType = HashMap.class;
                map = true;
                valType = Object.class;
            } else {
                objType = (Class<?>)type;
                fieldMap = new HashMap<>();
                types = new HashMap<>();
                Map<String, List<Field>> scopeList = new HashMap<>();
                List<Field> defaultScopeFields = new ArrayList<>();
                scopeList.put(DEFAULT_SCOPE, defaultScopeFields);

                Class<?> cls = objType;
                while( true ) {
                    for( Field field : cls.getDeclaredFields() ) {
                        String name = field.getName();
                        if( (field.getModifiers() & STATIC) != 0 || field.getAnnotation(JSON.Hidden.class) != null || fieldMap.containsKey(name) )
                            continue;

                        field.setAccessible(true);
                        if( field.getAnnotation(JSON.Inline.class) != null && Map.class.isAssignableFrom(field.getType()) ) {
                            inline = field;
                        } else {
                            fieldMap.put(name, field);
                            types.put(name, field.getGenericType());

                            JSON.Scope $Scope = field.getAnnotation(JSON.Scope.class);
                            if( $Scope == null ) {
                                for( List<Field> fields : scopeList.values() ) {
                                    fields.add(field);
                                }
                            } else {
                                for( String scope : $Scope.value() ) {
                                    List<Field> fields = scopeList.computeIfAbsent(scope, s -> new ArrayList<>(defaultScopeFields));
                                    fields.add(field);
                                }
                            }
                        }
                    }

                    if( cls.getAnnotation(JSON.Inherit.class) == null )
                        break;

                    cls = cls.getSuperclass();
                }

                fields = new HashMap<>();
                for( Map.Entry<String, List<Field>> entry : scopeList.entrySet() ) {
                    fields.put(entry.getKey(), entry.getValue().toArray(new Field[0]));
                }
            }

            try {
                constructor = objType.getDeclaredConstructor();
                constructor.setAccessible(true);
            } catch( NoSuchMethodException ignored ) {
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    public Object newInstance() {
        try {
            if( constructor == null ) {
                if( map ) {
                    return new HashMap<>();
                }
                throw new RuntimeException("Cannot create instance of " + type + ". Default constructor is not defined.");
            }
            Object obj = constructor.newInstance();
            if( inline != null ) {
                inline.set(obj, new HashMap<>());
            }
            return obj;
        } catch( Exception e ) {
            e.printStackTrace();
            return null;
        }
    }


    private Map<String, JSON.Serializer[]> serializers;

    public void toJson(JSON json, CharBuffer writer, Object val) {
        if( !map && serializers == null ) {
            synchronized( this ) {
                if( serializers == null ) {
                    Map<Field, JSON.Serializer> _sers = new HashMap<>(fieldMap.size());
                    for( Field f : fieldMap.values() ) {
                        _sers.put(f, JSON.getSerializer(f.getGenericType()));
                    }

                    serializers = new HashMap<>(fields.size());
                    for( Map.Entry<String, Field[]> entry : fields.entrySet() ) {
                        Field[] scopeFields = entry.getValue();
                        int size = scopeFields.length;
                        JSON.Serializer[] scopeSerializers = new JSON.Serializer[size];
                        for( int i = 0; i < size; i++ ) {
                            scopeSerializers[i] = _sers.get(scopeFields[i]);
                        }
                        serializers.put(entry.getKey(), scopeSerializers);
                    }
                }
            }
        }

        String scope = json.scope;
        Field[] fields = this.fields.get(scope);
        JSON.Serializer[] sers = serializers.get(scope);

        writer.append('{');
        boolean notEmpty = false;
        int len = fields.length;
        for( int i = 0; i < len; i++ ) {
            final JSON.Serializer ser = sers[i];
            try {
                Field field = fields[i];
                Object value = field.get(val);
                if( value != null ) {
                    writer.append('"').append(field.getName()).append(QUOTE_COLUMN);
                    ser.toJson(json, writer, value);
                    writer.append(',');
                    notEmpty = true;
                }
            } catch( Exception ignored ) {
                System.err.println(ser);
                ignored.printStackTrace();
            }
        }
        if( inline != null ) {
            try {
                Object map = inline.get(val);
                if( map != null ) {
                    for( Map.Entry<String, Object> entry : ((Map<String, Object>)map).entrySet() ) {
                        Object value = entry.getValue();
                        if( value != null ) {
                            writer.append('"').append(entry.getKey()).append(QUOTE_COLUMN);
                            json.stringify(writer, value, value.getClass());
                            writer.append(',');
                            notEmpty = true;
                        }
                    }
                }
            } catch( IllegalAccessException ignored ) {
            }
        }
        if( notEmpty )
            writer.back(1);
        writer.append('}');
    }

}
