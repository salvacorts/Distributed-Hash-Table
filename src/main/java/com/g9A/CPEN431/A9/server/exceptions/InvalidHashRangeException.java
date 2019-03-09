package com.g9A.CPEN431.A9.server.exceptions;

import com.g9A.CPEN431.A9.server.ServerNode;

public class InvalidHashRangeException extends Exception {
    @Override
    public String toString() {
        return "Hash end must be greater than start";
    }
}
