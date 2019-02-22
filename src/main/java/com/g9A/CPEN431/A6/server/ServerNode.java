package com.g9A.CPEN431.A6.server;

import com.g9A.CPEN431.A6.server.exceptions.InvalidHashRangeException;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServerNode {
    private InetAddress address;
    private int port;
    private int hashStart;
    private int hashEnd;

    public ServerNode(String host, int port, int hashStart, int hashEnd) throws InvalidHashRangeException,
                                                                                java.net.UnknownHostException {
        if (hashStart > hashEnd) throw new InvalidHashRangeException();

        this.address = InetAddress.getByName(host);
        this.port = port;
        this.hashStart = hashStart;
        this.hashEnd = hashEnd;
    }

    public boolean equals(Object o) {
        if (o == this) return true;
        if (! (o instanceof ServerNode)) return false;

        ServerNode other = (ServerNode) o;

        return other.getAddress().equals(address) || (other.getPort() == port);
    }

    public boolean inSpace(int hashNum) {
        return hashNum <= hashEnd && hashNum >= hashStart;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public int getHashStart() {
        return hashStart;
    }

    public int getHashEnd() {
        return hashEnd;
    }
}
