package com.g9A.CPEN431.A6.server.exceptions;

public class WrongNodeException extends Exception {
    private int hash;

    public WrongNodeException(int hash) {
        this.hash = hash;
    }

    public int getHash() {
        return hash;
    }
}
