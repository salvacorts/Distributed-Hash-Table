package com.g9A.CPEN431.A6.server.network;

import java.io.IOException;
import java.net.SocketException;
import java.util.List;
import java.util.Random;

import com.g9A.CPEN431.A6.client.Client;
import com.g9A.CPEN431.A6.server.Server;
import com.g9A.CPEN431.A6.server.ServerNode;
import com.g9A.CPEN431.A6.server.metrics.MetricsServer;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

public class FailureCheck implements Runnable {

    private Thread t;
    private boolean stopflag = false;
    private boolean firstFlag = true;
    
    Client client;
    Random rand = new Random();

    public FailureCheck(){
    	client = new Client("",0,0);
    }
    
    private void checkRandom() throws SocketException {
    	ServerNode node = null;
    	do {
        	int r = rand.nextInt(Server.serverNodes.size());
        	node = Server.serverNodes.get(r);
    	}while(node.equals(Server.selfNode));
    	client.changeServer(node.getAddress().getHostAddress(), node.getPort());
    	KVResponse kvr = null;
    	System.out.println("Pinging " + node.getAddress().getHostName() + ":" + node.getPort());
    	
    	try {
			kvr = client.DoRequest(6, "", "", 0);
		} catch (Exception e1) {
			removeNode(node);
			return;
		}
    	
    	if(kvr.getErrCode() != 0) {
			removeNode(node);
    	}
    }
    
    private void removeNode(ServerNode node) throws SocketException {
		Server.removeNode(node.getAddress().getHostAddress(), node.getPort());
		InternalRequest.DeadNodeRequest DNRequest = InternalRequest.DeadNodeRequest.newBuilder()
				.setServer(node.getAddress().getHostName())
				.setPort(node.getPort())
				.build();
		ByteString uuid = client.GetUUID();
		Epidemic epi = new Epidemic(DNRequest.toByteString(), uuid, 2);
		Server.epiQueue.add(epi);
    }

    public void run() {
    	if(firstFlag) {
    		firstFlag = false;
    		try {
				Thread.sleep(30*1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
    	
    	if(Server.serverNodes.size() > 1) {
			try {
				checkRandom();
			} catch (SocketException e) {
				e.printStackTrace();
			}
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
        firstFlag = true;

        if (t == null) {
            t = new Thread(this);
            t.start();
        }
    }

    public void stop() {
        stopflag = true;
    }
}
