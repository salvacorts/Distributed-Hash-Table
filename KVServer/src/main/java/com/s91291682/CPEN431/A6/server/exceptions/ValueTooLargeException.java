package com.s91291682.CPEN431.A6.server.exceptions;

public class ValueTooLargeException extends Exception {
    @Override
    public String toString() {
        return "Value parameter is too large";
    }
}
