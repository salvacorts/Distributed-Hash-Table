package com.g9A.CPEN431.A10.server.exceptions;

public class KeyTooLargeException extends Exception {
    @Override
    public String toString() {
        return "Key parameter is too large";
    }
}
