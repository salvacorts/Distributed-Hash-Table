package com.g9A.CPEN431.A8.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import com.g9A.CPEN431.A8.server.exceptions.InvalidHashRangeException;

public class ServerNode {
    private InetAddress address;
    private int id;
    private int port;
    private int epiPort;
    private List<HashSpace> hashSpaces;

    public ServerNode(String host, int port, int epiPort, int hashStart, int hashEnd, int id) throws InvalidHashRangeException,
                                                                                java.net.UnknownHostException {
        if (hashStart > hashEnd) throw new InvalidHashRangeException();
        hashSpaces = new ArrayList<HashSpace>();

        this.address = InetAddress.getByName(host);
        this.port = port;
        this.epiPort = epiPort;
        hashSpaces.add(new HashSpace(hashStart, hashEnd));
        this.id = id;
    }
    
    public ServerNode(String line, int id) throws IllegalArgumentException, UnknownHostException {
		String[] args = line.split(":");
        this.address = InetAddress.getByName(args[0]);
        this.port = Integer.parseInt(args[1]);
        this.epiPort = Integer.parseInt(args[2]);
        hashSpaces = new ArrayList<HashSpace>();
        this.id = id;
	}

    public void addHashSpace(int start, int end) {
    	hashSpaces.add(new HashSpace(start, end));
    }
    
    public void addHashSpaces(List<HashSpace> spaces) {
    	hashSpaces.addAll(spaces);
    }
    
    public List<HashSpace> getHashSpaces() {
    	return hashSpaces;
    }

    public boolean hasHashSpace(HashSpace h) {
    	return this.hashSpaces.contains(h);
    }
    
    public void removeHashSpace(HashSpace h) {
    	this.hashSpaces.remove(h);
    }

    public boolean equals(Object o) {
        if (o == this) return true;
        if (! (o instanceof ServerNode)) return false;

        ServerNode other = (ServerNode) o;

        //return other.getAddress().equals(address) && (other.getPort() == port);
        return other.getId() == this.id;
    }
    
    public int getId() {
    	return this.id;
    }

    public boolean inSpace(int hashNum) {
    	for(HashSpace h: hashSpaces) {
    		if(h.inSpace(hashNum)) {
    			return true;
    		}
    	}
    	return false;
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
}
