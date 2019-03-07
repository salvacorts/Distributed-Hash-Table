package com.g9A.CPEN431.A9.server.exceptions;

public class UnexistingKey extends Exception {
    @Override
    public String toString() {
        return "Key does not exist in the DB";
    }
}
