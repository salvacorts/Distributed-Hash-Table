package com.g9A.CPEN431.A6.server;

import com.g9A.CPEN431.A6.server.exceptions.InvalidHashRangeException;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServerNode {
    private InetAddress address;
    private int port;
    private int epiPort;
    private int hashStart;
    private int hashEnd;

    public ServerNode(String host, int port, int epiPort, int hashStart, int hashEnd) throws InvalidHashRangeException,
                                                                                java.net.UnknownHostException {
        if (hashStart > hashEnd) throw new InvalidHashRangeException();

        this.address = InetAddress.getByName(host);
        this.port = port;
        this.epiPort = epiPort;
        this.hashStart = hashStart;
        this.hashEnd = hashEnd;
    }
    
    public ServerNode(String line, int index, int total) throws IllegalArgumentException, UnknownHostException {
		String[] args = line.split(":");
        this.address = InetAddress.getByName(args[0]);
        this.port = Integer.parseInt(args[1]);
        this.epiPort = Integer.parseInt(args[2]);

		this.hashStart = index == 0 ? 0 : index*255/total + 1;
		this.hashEnd = (index+1)*255/total;
		if (hashStart > hashEnd) {
			throw new IllegalArgumentException("Hash end must be greater than start");
		}
	}
    
    public void setHashRange(int start, int end) {
    	hashStart = start;
    	hashEnd = end;
    }

    public boolean equals(Object o) {
        if (o == this) return true;
        if (! (o instanceof ServerNode)) return false;

        ServerNode other = (ServerNode) o;

        return other.getAddress().equals(address) && (other.getPort() == port);
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
    
    public int getEpiPort() {
    	return epiPort;
    }

    public int getHashStart() {
        return hashStart;
    }

    public int getHashEnd() {
        return hashEnd;
    }
}
