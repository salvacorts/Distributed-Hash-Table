package com.g9A.CPEN431.A6.server.exceptions;

public class InvalidHashRangeException extends Exception {
    @Override
    public String toString() {
        return "Hash end must be greater than start";
    }
}
