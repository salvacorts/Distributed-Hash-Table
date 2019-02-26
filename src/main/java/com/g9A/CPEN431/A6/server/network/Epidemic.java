package com.g9A.CPEN431.A6.server.network;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;

import com.g9A.CPEN431.A6.client.Client;
import com.g9A.CPEN431.A6.server.Server;
import com.g9A.CPEN431.A6.server.ServerNode;
import com.g9A.CPEN431.A6.utils.ByteOrder;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;
import ca.NetSysLab.ProtocolBuffers.Message;

public class Epidemic implements Runnable {

    private boolean stopflag = false;
    
    ByteString payload;
    int type;
    long epId;
    Client client;
    Random rand = new Random();
    
    private int iterations;

    public Epidemic(ByteString payload, int type){
    	client = new Client("",0,3);
    	this.payload = payload;
    	this.type = type;
    	iterations = Server.serverNodes.size();

    	if (iterations < 10) {
    		iterations = (10 - iterations) * 2;
    	}
    }
    
    public Epidemic(ByteString payload, int type, long epId){
    	client = new Client("",0,3);
    	this.payload = payload;
    	this.type = type;
    	this.epId = epId;
    	iterations = Server.serverNodes.size();

    	if (iterations < 10) {
    		iterations = (10 - iterations) * 2;
    	}
    }
    
    public void generateId(String svr, int epiPort) {
    	CRC32 crc = new CRC32();

    	crc.update(svr.getBytes());
    	crc.update(epiPort);

    	this.epId = crc.getValue();
    }
    
    private void sendRandom() throws IOException {

    	// If there is only one node (this one) there is nothing to spread
    	if (Server.serverNodes.size() < 2) {
    		stopflag = true;
    		return;
		}

    	// Pick a node randomly
    	ServerNode node;

    	do {
	    	int r = rand.nextInt(Server.serverNodes.size());
	    	node = Server.serverNodes.get(r);
    	} while(node.equals(Server.selfNode));

    	// Send the payload to that node
    	System.out.println("[Epidemic] sending to " + node.getAddress().getHostName() + ":" + node.getEpiPort());
    	client.changeServer(node.getAddress().getHostAddress(), node.getEpiPort());
    	client.DoInternalRequest(payload, type, epId);

    	iterations--;
    }
    
    public void run() {

    	while (!stopflag && iterations > 0) {
			try {
				sendRandom();
				Thread.sleep(5000);
			} catch (IOException e) {
				System.out.println("Epidemic failure");
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
    }

    public void start() {
        stopflag = false;
        run();
    }

    public void stop() {
        stopflag = true;
    }

    @Override
    public boolean equals(Object other) {
    	if (other instanceof Epidemic) return this.epId == ((Epidemic) other).epId;

    	if (other instanceof Integer) return this.epId == (Integer) other;

    	return false;
	}
}
