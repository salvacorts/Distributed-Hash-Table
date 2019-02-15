package com.s91291682.CPEN431.A6.server.exceptions;

public class MissingParameterException extends Exception {
    @Override
    public String toString() {
        return "Missing parameter(s) in request";
    }
}
