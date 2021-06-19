package ru.xeloo.xml;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class EnumValues {

    private static final Map<Class<? extends Enum<?>>, Map<String, Enum<?>>> strToObj = new HashMap<>();
    private static final Map<Class<? extends Enum<?>>, Map<Enum<?>, String>> objToStr = new HashMap<>();

    public static Object getValue(Class<? extends Enum<?>> type, String str) {
        Map<String, Enum<?>> map = strToObj.get(type);
        if( map == null ) {
            parseType(type);
            map = strToObj.get(type);
        }
        return map.get(str);
    }

    public static String toString(Enum<?> val) {
        Class<? extends Enum<?>> type = val.getDeclaringClass();
        Map<Enum<?>, String> map = objToStr.get(type);
        if( map == null ) {
            parseType(type);
            map = objToStr.get(type);
        }
        return map.get(val);
    }

    private static void parseType(Class<? extends Enum<?>> type) {
        Field fld;
        try {
            fld = type.getField("value");
            fld.setAccessible(true);
            if( fld.getType() != String.class )
                fld = null;
        } catch( Exception ignored ) {
            fld = null;
        }
        Map<String, Enum<?>> strToObj = new HashMap<>();
        Map<Enum<?>, String> objToStr = new HashMap<>();
        for( Enum<?> o : type.getEnumConstants() ) {
            try {
                String str = fld != null ? (String)fld.get(o) : o.toString();
                strToObj.put(str, o);
                objToStr.put(o, str);
            } catch( IllegalAccessException ignored ) {
                // never throws
            }
        }

        EnumValues.strToObj.put(type, strToObj);
        EnumValues.objToStr.put(type, objToStr);
    }
}
