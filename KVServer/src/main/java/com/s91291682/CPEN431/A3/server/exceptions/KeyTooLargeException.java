package com.s91291682.CPEN431.A3.server.exceptions;

public class KeyTooLargeException extends Exception {
    @Override
    public String toString() {
        return "Key parameter is too large";
    }
}
