package com.g9A.CPEN431.A6.server.network;

import java.util.List;
import java.util.Random;

import com.g9A.CPEN431.A6.client.Client;
import com.g9A.CPEN431.A6.server.Server;
import com.g9A.CPEN431.A6.server.ServerNode;
import com.g9A.CPEN431.A6.server.metrics.MetricsServer;

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

    public void run() {
    	if(firstFlag) {
    		firstFlag = false;
    		try {
				Thread.sleep(30*1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
    	
    	int r = rand.nextInt(Server.serverNodes.size());
    	ServerNode node = Server.serverNodes.get(r);
    	client.changeServer(node.getAddress().getHostAddress(), node.getPort());
    	KVResponse kvr = null;
    	
    	try {
			kvr = client.DoRequest(6, "", "", 0);
		} catch (Exception e1) {
			System.out.println("FailureCheck failure");
		}
    	
    	if(kvr.getErrCode() == 0) {
    		Server.removeNode(node.getAddress().getHostAddress(), node.getPort());
    		InternalRequest.DeadNodeRequest DNRequest = InternalRequest.DeadNodeRequest.newBuilder()
    				.setServer(node.getAddress().getHostName())
    				.setPort(node.getPort())
    				.build();
    		Epidemic epi = new Epidemic(DNRequest.toByteString(), 2);
    		epi.start();
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
