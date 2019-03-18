package com.g9A.CPEN431.A11.server.exceptions;

public class MissingParameterException extends Exception {
    @Override
    public String toString() {
        return "Missing parameter(s) in request";
    }
}
