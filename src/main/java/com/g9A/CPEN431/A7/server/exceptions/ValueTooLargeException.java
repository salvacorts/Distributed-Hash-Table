package com.g9A.CPEN431.A7.server.exceptions;

public class ValueTooLargeException extends Exception {
    @Override
    public String toString() {
        return "Value parameter is too large";
    }
}
