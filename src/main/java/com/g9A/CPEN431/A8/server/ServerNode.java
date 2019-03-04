package com.g9A.CPEN431.A8.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.g9A.CPEN431.A8.server.exceptions.InvalidHashRangeException;

public class ServerNode {
    private InetAddress address;
    private int port;
    private int epiPort;
    private int[] hashValues;

    public ServerNode(String host, int port, int epiPort, int [] hashes) throws InvalidHashRangeException,
                                                                                java.net.UnknownHostException {
        hashValues = hashes;

        this.address = InetAddress.getByName(host);
        this.port = port;
        this.epiPort = epiPort;
    }
    
    public ServerNode(String line) throws IllegalArgumentException, UnknownHostException {
		String[] args = line.split(":");
        this.address = InetAddress.getByName(args[0]);
        this.port = Integer.parseInt(args[1]);
        this.epiPort = Integer.parseInt(args[2]);

        Random r = new Random();
        if(args.length <= 3) {
            hashValues = new int[2];
            for(int i = 0; i < hashValues.length; i++) {
            	hashValues[i] = r.nextInt(256);
            }
        }
        hashValues = new int[args.length-3];
        for(int i = 3; i < args.length; i++) {
        	hashValues[i-3] = Integer.parseInt(args[i]);
        }
	}

    public int[] getHashValues() {
    	return hashValues;
    }

    public boolean equals(Object o) {
        if (o == this) return true;
        if (! (o instanceof ServerNode)) return false;

        ServerNode other = (ServerNode) o;

        return other.getAddress().equals(address) && (other.getPort() == port);
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
