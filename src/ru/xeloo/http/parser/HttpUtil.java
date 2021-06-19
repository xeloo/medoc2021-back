package ru.xeloo.http.parser;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class HttpUtil {

    /**
     * Remove escape of form \xhh and \?
     *
     * @param str
     * @return
     */
    public static String unescapeUrl(String str) throws IOException {
        char[] chars = str.toCharArray();
        StringBuilder sb = new StringBuilder(chars.length);
        for( int i = 0; i < chars.length; ) {
            char ch = chars[i++];
            if( ch == '\\' ) {
                if( i == chars.length )
                    throw new IOException("Invalid char '\\' at the end of string: " + str);
                ch = chars[i++];
                if( ch == 'x' ) {
                    int e = i + 2;
                    if( e > chars.length )
                        throw new IOException("Invalid escape '\\xhh' at the end of string: " + str);
                    sb.append(Character.toChars(Integer.parseInt(str.substring(i, e), 16)));
                    i = e;
                    continue;
                }
                if( ch == 'u' ) {
                    int e = i + 4;
                    if( e > chars.length )
                        throw new IOException("Invalid escape '\\uhhhh' at the end of string: " + str);
                    sb.append(Character.toChars(Integer.parseInt(str.substring(i, e), 16)));
                    i = e;
                    continue;
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Decode string from form '%hh%hh%hh'
     *
     * @param str encoded string
     * @return decoded string
     */
    public static String decodeUrl(String str) {
        try {
            return decodeUrl(str, "utf-8");
        } catch( UnsupportedEncodingException e ) {
            return str;
        }
    }

    public static String decodeUrl(String str, String charset) throws UnsupportedEncodingException {
        char[] chars = str.toCharArray();
        byte[] bytes = new byte[chars.length];
        int n = 0;
//		StringBuilder sb = new StringBuilder(chars.length);
        try {
            for( int i = 0; i < chars.length; ) {
                if( chars[i] != '%' ) {
                    bytes[n++] = (byte)chars[i++];
//					sb.append(chars[i++]);
                } else {
                    bytes[n++] = (byte)Integer.parseInt(str.substring(++i, i + 2), 16);
//					sb.append(Character.toChars(Integer.parseInt(str.substring(++i, i + 2), 16)));
                    i += 2;
                }
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
        return new String(bytes, 0, n, charset);
//		return sb.toString();
    }

    /**
     * Encode string to form '%hh%hh%hh'
     *
     * @param str decoded string
     * @return encoded string
     * @throws IOException
     */
    public static String encodeUrl(String str) {
        try {
            return encodeUrl(str, "utf-8");
        } catch( UnsupportedEncodingException e ) {
            return str;
        }
    }

    public static String encodeUrl(String str, String charset) throws UnsupportedEncodingException {
        byte[] chars = str.getBytes(charset);
        StringBuilder sb = new StringBuilder(chars.length * 3);
        for( byte ch : chars ) {
            sb.append('%').append(Integer.toString((ch & 0xff) + 0x100, 16).toUpperCase().substring(1));
        }
        return sb.toString();
    }

}
