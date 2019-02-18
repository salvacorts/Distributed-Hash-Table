package com.s91291682.CPEN431.A6.server;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServerNode {
	private InetAddress address;
	private int port;
	private int hashStart;
	private int hashEnd;
	
	public ServerNode(String host, int port, int hashStart, int hashEnd) throws Exception {
		this.address = InetAddress.getByName(host);
		this.port = port;
		this.hashStart = hashStart;
		this.hashEnd = hashEnd;
		if(hashStart > hashEnd) {
			throw new Exception("Hash end must be greater than start");
		}
	}
	
	public ServerNode(String line) throws IllegalArgumentException, UnknownHostException {
		String[] args = line.split(" ");
		this.address = InetAddress.getByName(args[0]);
		this.port = Integer.parseInt(args[1]);
		this.hashStart = Integer.parseInt(args[2]);
		this.hashEnd = Integer.parseInt(args[3]);
		if(hashStart > hashEnd) {
			throw new IllegalArgumentException("Hash end must be greater than start");
		}
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
}
