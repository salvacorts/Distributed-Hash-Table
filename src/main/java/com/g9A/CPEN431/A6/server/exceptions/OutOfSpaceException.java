package com.g9A.CPEN431.A6.server.exceptions;

public class OutOfSpaceException extends Exception {
    @Override
    public String toString() {
        return "Cannot store more data in KVMap";
    }
}
