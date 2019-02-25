package com.g9A.CPEN431.A6.server.network;

import java.io.IOException;
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

public class Epidemic {

    private boolean stopflag = false;
    
    ByteString payload;
    int type;
    ByteString uuid;
    Client client;
    Random rand = new Random();
    
    private int iterations;

    public Epidemic(ByteString payload, ByteString uuid, int type){
    	client = new Client("",0,3);
    	this.uuid = uuid;
    	this.payload = payload;
    	this.type = type;
    	iterations = Server.serverNodes.size();
    }
    
    private void sendRandom() throws IOException {
    	ServerNode node = null;
    	do {
	    	int r = rand.nextInt(Server.serverNodes.size());
	    	node = Server.serverNodes.get(r);
	    	if(Server.serverNodes.size() < 2) {
	    		stopflag = true;
	    		return;
	    	}
    	} while(node.equals(Server.selfNode));
    	
    	System.out.println("epidemic sending to " + node.getAddress().getHostName() + ":" + node.getPort());
    	client.changeServer(node.getAddress().getHostAddress(), node.getPort());
    	client.DoInternalRequest(payload, uuid, type);
    	
    	if(iterations-- > 0){
    		stopflag = true;
    	}
    }
    
    public void run() {
    	try {
			sendRandom();
		} catch (IOException e) {
			System.out.println("Epidemic failure");
			e.printStackTrace();
		}
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(!stopflag) this.run();
    }

    public void start() {
        stopflag = false;
        run();
    }

    public void stop() {
        stopflag = true;
    }
}
