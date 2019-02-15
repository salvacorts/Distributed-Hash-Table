package com.s91291682.CPEN431.A6.server.exceptions;

public class UnexistingKey extends Exception {
    @Override
    public String toString() {
        return "Key does not exist in the DB";
    }
}
