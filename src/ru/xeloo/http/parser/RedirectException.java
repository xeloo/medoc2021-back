package ru.xeloo.http.parser;

import java.io.IOException;

public class RedirectException extends IOException {

    private final String location;

    public RedirectException(String location, String message) {
        super(message);
        this.location = location;
    }

    public String getLocation() {
        return location;
    }
}
