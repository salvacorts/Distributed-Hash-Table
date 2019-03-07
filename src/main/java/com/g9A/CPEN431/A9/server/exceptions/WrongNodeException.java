package com.g9A.CPEN431.A9.server.exceptions;

public class WrongNodeException extends Exception {
    private final int hash;

    public WrongNodeException(int hash) {
        this.hash = hash;
    }

    public int getHash() {
        return hash;
    }
}
