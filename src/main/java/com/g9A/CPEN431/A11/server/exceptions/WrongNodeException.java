package com.g9A.CPEN431.A11.server.exceptions;

import com.g9A.CPEN431.A11.server.ServerNode;

public class WrongNodeException extends Exception {
	public ServerNode trueNode;

    public WrongNodeException(ServerNode trueNode) {
        this.trueNode = trueNode;
    }
}
