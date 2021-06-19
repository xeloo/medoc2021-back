package org.xsite.webapp.params;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.xsite.server.FilePart;
import org.xsite.webapp.AppModule;

import static org.xsite.util.Util.isValidEmailAddress;
import static org.xsite.util.Util.isValidUUID;

public class Param {

    static final int T_UNDEFINED = -1;
    static final int T_STRING = 0;
    static final int T_INTEGER = 1;
    static final int T_FLOAT = 2;
    static final int T_BOOLEAN = 3;
    static final int T_EMAIL = 4;
    static final int T_PHONE = 5;
    static final int T_FILE = 6;

    private static final Map<Class<?>, Integer> typeMap = Map.of(
        String.class, T_STRING,
        Integer.class, T_INTEGER,
        FilePart.class, T_FILE
    );

    final String name;
    Object value;
    int type;
    List<String> errors;

    public Param(String name, Object value) {
        this.name = name;
        this.value = value;
        this.type = value == null ? T_UNDEFINED : typeMap.getOrDefault(value.getClass(), T_UNDEFINED);
    }

    public Param check(Rule... rules) {
        for( Rule rule : rules ) {
            String error = rule.check(this);
            if( error != null ) {
                error(error);
            }
        }
        if( errors != null ) {
            Map<String, List<String>> errorMap = AppModule.validationErrors();
            errorMap.put(name, errors);
        }
        return this;
    }

    public void error(String e) {
        if( errors == null ) {
            errors = new ArrayList<>();
            Map<String, List<String>> errorMap = AppModule.validationErrors();
            errorMap.put(name, errors);
        }
        errors.add(e);
    }

    public String getString() {
        return value != null ? value.toString() : null;
    }

    public List<String> getStringList() {
        if( value == null )
            return null;
        String str = value.toString();
        if( str.startsWith("[") && str.endsWith("]") )
            str = str.substring(1, str.length() - 1);
        return List.of(str.split(",\\s*"));
    }

    public String getStringOr(String def) {
        return value != null ? value.toString() : def;
    }

    public boolean getBool() {
        return switch( type ) {
            case T_BOOLEAN -> (Boolean)value;
            case T_STRING -> value.equals("true") || value.equals("1");
            case T_INTEGER -> (Integer)value == 1;
            default -> false;
        };
    }

    public int getInt() {
        try {
            return switch( type ) {
                case T_INTEGER -> (Integer)value;
                case T_STRING -> Integer.parseInt((String)value);
                case T_BOOLEAN -> (Boolean)value ? 1 : 0;
                default -> 0;
            };
        } catch( Exception e ) {
            return 0;
        }
    }

    public int[] getIntArray(String sep) {
        System.err.println("getIntArray() is not implemented");
        return new int[0];
/*
        try {
            String[] strArr = value.split(sep);
            int[] intArr = new int[strArr.length];
            for( int i = 0; i < strArr.length; i++ ) {
                intArr[i] = Integer.parseInt(strArr[i]);
            }
            return intArr;
        } catch( NumberFormatException e ) {
            return new int[0];
        }
*/
    }

    public FilePart getFile() {
        return (FilePart)value;
    }


    public static Rule FILE = p -> {
        if( p.type != T_FILE )
            return "not_file: Expected file";
        return null;
    };

    public static Rule FILE(String contentType) {
        return FILE(contentType, null);
    }

    public static Rule FILE(String type, String error) {
        return p -> {
            if( p.type != T_FILE ) {
                return "not_file: Expected file";
            } else {
                FilePart part = (FilePart)p.value;
                String e = error != null ? error : "incorrect_type";
                if( type.charAt(0) == '.' ) {
                    if( !part.fileName.endsWith(type) ) {
                        return "bad_content_type: Expected: " + type;
                    }
                } else {
                    if( !part.contentType.equalsIgnoreCase(type) ) {
                        return "bad_content_type: Expected: " + type;
                    }
                }
            }
            return null;
        };
    }

    public static Rule EMAIL = p -> {
        if( p.value != null && !(p.type == T_STRING && isValidEmailAddress((String)p.value)) )
            return "not_email: Should be valid e-mail address";
        return null;
    };

    public static Rule IS_UUID = p -> {
        if( p.value != null && !(p.type == T_STRING && isValidUUID((String)p.value)) )
            return "not_uuid: Expected UUID format: ########-####-####-####-############";
        return null;
    };

    public static Rule NOT_EMPTY = p -> {
        if( p.value == null || p.type == T_STRING && ((String)p.value).isEmpty() )
            return "empty: Value should be provided";
        return null;
    };
    public static Rule NULL_EMPTY = p -> {
        if( p.value != null && p.type == T_STRING && ((String)p.value).isEmpty() )
            p.value = null;
        return null;
    };
    public static Rule INTEGER = p -> {
        if( p.type != T_INTEGER ) {
            if( p.type == T_STRING ) {
                try {
                    p.value = Integer.parseInt((String)p.value);
                    p.type = Param.T_INTEGER;
                    return null;
                } catch( NumberFormatException ignored ) {
                }
            }
            return "not_integer: Expected integer value";
        }
        return null;
    };

    public static Rule EQUALS(String str) {
        return EQUALS(str, null);
    }

    public static Rule EQUALS(String str, String error) {
        return p -> {
            if( !Objects.equals(str, p.value) ) {
                return "not_equals: " + (error != null ? error : "Expected '" + str + "'");
            }
            return null;
        };
    }

    public static Rule MATCH(String regex) {
        return MATCH(regex, null);
    }

    public static Rule MATCH(String regex, String error) {
        return p -> {
            if( p.value == null || !Pattern.matches(regex, p.value.toString()) ) {
                return "not_match: " + (error != null ? error : "Expected /" + regex + "/");
            }
            return null;
        };
    }

    public static Rule MIN(int min) {
        return p -> {
            if( p.type == Param.T_INTEGER ) {
                if( (Integer)p.value < min ) {
                    return "too_small: Min value: " + min;
                }
            } else if( p.type == Param.T_STRING ) {
                if( p.value != null && ((String)p.value).length() < min ) {
                    return "too_short: Min length: " + min;
                }
            }
            return null;
        };
    }

    public static Rule MAX(int max) {
        return p -> {
            if( p.type == Param.T_INTEGER ) {
                if( (Integer)p.value > max ) {
                    return "too_big: Max value: " + max;
                }
            } else if( p.type == Param.T_STRING ) {
                if( p.value != null && ((String)p.value).length() > max ) {
                    return "too_long: Max length: " + max;
                }
            }
            return null;
        };
    }

    public static Rule RANGE(int min, int max) {
        return p -> {
            String err = MIN(min).check(p);
            if( err != null )
                return err;
            return MAX(max).check(p);
        };
    }

    public interface Rule {

        String check(Param p);

    }

}
