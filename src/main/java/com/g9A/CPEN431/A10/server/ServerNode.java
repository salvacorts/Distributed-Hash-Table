package com.g9A.CPEN431.A10.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.g9A.CPEN431.A10.server.exceptions.InvalidHashRangeException;

public class ServerNode {
    private InetAddress address;
    private int id;
    private int port;
    private int epiPort;
    private int[] hashValues;

    public ServerNode(String host, int port, int epiPort, int id, int [] hashes) throws InvalidHashRangeException, UnknownHostException {
        hashValues = hashes;

        this.address = InetAddress.getByName(host);
        this.port = port;
        this.epiPort = epiPort;
        this.id = id;
    }
    
    public ServerNode(String line, int id) throws IllegalArgumentException, UnknownHostException {
		String[] args = line.split(":");
        this.address = InetAddress.getByName(args[0]);
        this.port = Integer.parseInt(args[1]);
        this.epiPort = Integer.parseInt(args[2]);
        this.id = id;

        Random r = new Random();
        if(args.length < 4) {
            System.err.println("Missing hashspace args");
        }
        else {
	        hashValues = new int[args.length-3];
	        for(int i = 3; i < args.length; i++) {
	        	hashValues[i-3] = Integer.parseInt(args[i]);
	        }
        }
	}

    public int[] getHashValues() {
    	return hashValues;
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
