package com.g9A.CPEN431.A10.server.exceptions;

import com.g9A.CPEN431.A10.server.ServerNode;

public class InvalidHashRangeException extends Exception {
    @Override
    public String toString() {
        return "Hash end must be greater than start";
    }
}
