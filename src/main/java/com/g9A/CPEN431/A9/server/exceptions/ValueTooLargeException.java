package com.g9A.CPEN431.A9.server.exceptions;

public class ValueTooLargeException extends Exception {
    @Override
    public String toString() {
        return "Value parameter is too large";
    }
}
