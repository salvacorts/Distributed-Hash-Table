package com.g9A.CPEN431.A6.server.network;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;
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

    private Thread t;
    private boolean stopflag = false;
    
    ByteString payload;
    int type;
    Client client;
    Random rand = new Random();
    
    private int count = 0;

    public Epidemic(ByteString payload, int type){
    	client = new Client("",0,0);
    	this.payload = payload;
    	this.type = type;
    }
    
    private void sendRandom() throws IOException {
    	int r = rand.nextInt(Server.serverNodes.size());
    	ServerNode node = Server.serverNodes.get(r);
    	client.changeServer(node.getAddress().getHostAddress(), node.getPort());
    	
    	client.DoInternalRequest(payload, type);
    	count++;
    	
    	if(count < Server.serverNodes.size()) {
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

        if (t == null) {
            t = new Thread(this);
            t.start();
        }
    }

    public void stop() {
        stopflag = true;
    }
}
