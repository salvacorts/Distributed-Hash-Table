package com.g9A.CPEN431.A9.server.exceptions;

public class MissingParameterException extends Exception {
    @Override
    public String toString() {
        return "Missing parameter(s) in request";
    }
}