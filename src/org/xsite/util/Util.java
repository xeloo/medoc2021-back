package org.xsite.util;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Util {

    private static final Random RANDOM = new Random();

    private static final Map<String, String> extContentTypeMap = new HashMap<>();
    static {
        extContentTypeMap.put("css", "text/css");
        extContentTypeMap.put("csv", "text/csv");
        extContentTypeMap.put("html", "text/html");
        extContentTypeMap.put("txt", "text/plain");
        extContentTypeMap.put("md", "text/markdown");
        extContentTypeMap.put("json", "application/json");
        extContentTypeMap.put("js", "application/javascript");
        extContentTypeMap.put("pdf", "application/pdf");
        extContentTypeMap.put("zip", "application/zip");
        extContentTypeMap.put("xml", "application/xml");
        extContentTypeMap.put("doc", "application/msword");
        extContentTypeMap.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        extContentTypeMap.put("xls", "application/vnd.ms-excel");
        extContentTypeMap.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        extContentTypeMap.put("ppt", "application/vnd.ms-powerpoint");
        extContentTypeMap.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        extContentTypeMap.put("gif", "image/gif");
        extContentTypeMap.put("jpeg", "image/jpeg");
        extContentTypeMap.put("png", "image/png");
        extContentTypeMap.put("svg", "image/svg+xml");
        extContentTypeMap.put("tiff", "image/tiff");
        extContentTypeMap.put("ico", "image/vnd.microsoft.icon");
        extContentTypeMap.put("webp", "image/webp");
        extContentTypeMap.put("mp4", "video/mp4");
    }
    private static final String UNKNOWN_CONTENT_TYPE = "application/octet-stream";

    public static String getContentType(String fileName) {
        int i = fileName.lastIndexOf('.');
        String contentType = i != -1 ? extContentTypeMap.get(fileName.substring(i + 1)) : null;
        return contentType == null ? UNKNOWN_CONTENT_TYPE : contentType;
    }

    public static boolean compareUrls(String u1, String u2) {
        boolean t1 = u1.charAt(u1.length() - 1) == '/';
        boolean t2 = u2.charAt(u2.length() - 1) == '/';
        return t1 == t2 ? u1.equals(u2) :
               t1 ? u1.substring(0, u1.length() - 1).equals(u2) :
               u1.equals(u2.substring(0, u2.length() - 1));


        //	return (if (u1.endsWith('/')) u1 else "$u1/").startsWith(if (u2.endsWith('/')) u2 else "$u2/")
    }


    public static boolean isValidEmailAddress(String email) {
        return email.matches("^[a-zA-Z0-9._-]+@(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,})$");
    }

    public static boolean isValidUUID(String uuid) {
        return uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
    private static final String PASSWORD_CHARS_EX = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890!@#$%^&*(),<.>/?_+-=~`;:'\"";

    public static String generatePassword(int length, boolean simple) {
        StringBuilder sb = new StringBuilder();
        String source = simple ? PASSWORD_CHARS : PASSWORD_CHARS_EX;
        int size = source.length();
        for( int i = 0; i < length; i++ ) {
            sb.append(source.charAt(RANDOM.nextInt(size)));
        }
        return sb.toString();
    }

    public static String removeHtmlTags(String text) {
        return text.replaceAll("<[^>]*>", "");
    }

    public static Map<String, Object> map(Object... args) {
        Map<String, Object> map = new HashMap<>();
        for( int i = 0; i < args.length - 1; i += 2 ) {
            if( args[i] instanceof String ) {
                map.put((String)args[i], args[i + 1]);
            } else {
                throw new IllegalArgumentException("Parameter " + (i + 1) + " should be a String");
            }
        }
        return map;
    }

    public static String urlParams(Object... args) {
        if( args.length >= 2 ) {
            StringBuilder sb = new StringBuilder();
            for( int i = 0; i < args.length - 1; i += 2 ) {
                if( args[i] instanceof String ) {
                    sb.append(args[i]).append("=").append(URLEncoder.encode(String.valueOf(args[i + 1]), UTF_8)).append("&");
                } else {
                    throw new IllegalArgumentException("Parameter " + (i + 1) + " should be a String");
                }
            }
            sb.setLength(sb.length() - 1);
            return sb.toString();
        } else {
            return "";
        }
    }

    public static String generatePinCode(int length) {
        StringBuilder sb = new StringBuilder();
        for( int i = 0; i < length; i++ ) {
            sb.append((int)(Math.random() * 10));
        }
        return sb.toString();
    }

}
