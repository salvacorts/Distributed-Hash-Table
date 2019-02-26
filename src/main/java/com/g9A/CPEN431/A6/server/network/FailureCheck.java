package com.g9A.CPEN431.A6.server.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.List;
import java.util.Random;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import com.g9A.CPEN431.A6.client.Client;
import com.g9A.CPEN431.A6.client.exceptions.UnsupportedCommandException;
import com.g9A.CPEN431.A6.server.Server;
import com.g9A.CPEN431.A6.server.ServerNode;
import com.g9A.CPEN431.A6.server.Worker;
import com.g9A.CPEN431.A6.server.metrics.MetricsServer;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

import javax.xml.crypto.Data;

public class FailureCheck implements Runnable {

    private Thread t;
    private boolean stopflag = false;
    private boolean firstFlag = true;
    private DatagramSocket socket;

    Random rand = new Random();

    public FailureCheck() throws SocketException {
    	this.socket = new DatagramSocket();
    }

	/**
	 * Check if a random node is infected
	 * @throws SocketException
	 */
	private void checkRandom() throws SocketException {
		// If there is only one node (this one), return, there is nothing to check
    	if (Server.serverNodes.size() < 2) return;

    	// Randomly select a node to check
		ServerNode node;

		do {
        	int r = rand.nextInt(Server.serverNodes.size());
        	node = Server.serverNodes.get(r);
    	} while(node.equals(Server.selfNode));

		// Set the client to use this server
    	//client.changeServer(node.getAddress().getHostAddress(), node.getEpiPort());

    	try {
			// Send isAlive to the node
			ByteString uuid = Client.GetUUID();
			KeyValueRequest.KVRequest request = KeyValueRequest.KVRequest.newBuilder().setCommand(6).build();
			Message.Msg msg = Client.PackMessage(request, uuid.toByteArray(), 1);

			// Serialize to packet
			DatagramPacket send_packet = new DatagramPacket(msg.toByteArray(), msg.getSerializedSize(), node.getAddress(), node.getPort());

			// Send packet
			KVResponse kvr = Worker.SendAndReceive(socket, send_packet, uuid, 8);

			// if sth went wrong
			if (kvr.getErrCode() != 0) removeNode(node);

		} catch (IOException e) {	// If could not establish contact with the node, remove the node
			System.out.println("[FailureCheck] Server " + node.getAddress().getHostName() + ":" + node.getPort() + " is down");
			removeNode(node);
		}
    }
    
    public static void removeNode(ServerNode node) {
		Server.removeNode(node.getAddress().getHostAddress(), node.getPort());

		InternalRequest.DeadNodeRequest DNRequest = InternalRequest.DeadNodeRequest.newBuilder()
				.setServer(node.getAddress().getHostName())
				.setPort(node.getPort())
				.build();

		// Create epidemic containing DNRequest
		Epidemic epi = new Epidemic(DNRequest.toByteString(), 2);
		epi.generateId(node.getAddress().getHostAddress(), node.getEpiPort());

		//  Spread the epidemic across nodes
		Server.epiSrv.add(epi);
    }

    public void run() {
    	if (firstFlag) {
    		firstFlag = false;
    		try {
				Thread.sleep(2*60*1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
    	
    	if (Server.serverNodes.size() > 1) {
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

        if (!stopflag) this.run();
    }

    public void start() {
        stopflag = false;
        firstFlag = true;

        if (t == null) {
            t = new Thread(this);
			t.setPriority(Thread.MAX_PRIORITY);
			t.start();
        }
    }

    public void stop() {
        stopflag = true;
    }
}
