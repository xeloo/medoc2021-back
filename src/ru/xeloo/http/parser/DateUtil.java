package ru.xeloo.http.parser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {

    private static final SimpleDateFormat shortDateFormat = new SimpleDateFormat("dd.MM.yyyy");

    public static Date shortDate(String date) {
        try {
            return shortDateFormat.parse(date);
        } catch( ParseException e ) {
            return null;
        }
    }

}
