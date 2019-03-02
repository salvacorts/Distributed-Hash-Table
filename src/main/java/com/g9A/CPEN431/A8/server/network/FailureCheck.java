package com.g9A.CPEN431.A8.server.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Random;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;

import com.g9A.CPEN431.A8.client.Client;
import com.g9A.CPEN431.A8.server.Server;
import com.g9A.CPEN431.A8.server.ServerNode;
import com.g9A.CPEN431.A8.server.Worker;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

public class FailureCheck implements Runnable {
	private static boolean FIRST_TIME_FLAG = true;
	private static boolean STOP_FLAG = false;
    private DatagramSocket socket;
	private Thread t;

    public FailureCheck() throws SocketException {
    	this.socket = new DatagramSocket();
    }

	/**
	 * Check if a random node is infected
	 * @throws SocketException
	 */
	private void checkRandom() throws SocketException {
		// If there is only one node (this one), return, there is nothing to check
    	if (Server.ServerNodes.size() < 2) return;

    	// Randomly select a node to check
		ServerNode node;
		Random rand = new Random();

		do {
        	int r = rand.nextInt(Server.ServerNodes.size());
        	node = Server.ServerNodes.get(r);
    	} while(node.equals(Server.selfNode));


    	try {
			// Send isAlive to the node
			ByteString uuid = Worker.GetUUID(socket);
			KeyValueRequest.KVRequest request = KeyValueRequest.KVRequest.newBuilder().setCommand(6).build();
			Message.Msg msg = Client.PackMessage(request, uuid.toByteArray(), 1);

			// Serialize to packet
			// TODO: Check if by using the normal port it still works well enough
			DatagramPacket send_packet = new DatagramPacket(msg.toByteArray(), msg.getSerializedSize(), node.getAddress(), node.getPort());

			// Send packet
			KVResponse kvr = Worker.SendAndReceive(socket, send_packet, uuid, 5);

			// if sth went wrong
			if (kvr.getErrCode() != 0) removeNode(node);

		} catch (IOException e) {	// If could not establish contact with the node, remove the node
			System.out.println("[FailureCheck] Server " + node.getAddress().getHostName() + ":" + node.getPort() + " is down");
			removeNode(node);
		}
    }
    
    public void removeNode(ServerNode node) throws java.net.SocketException {
		Server.removeNode(node.getAddress().getHostAddress(), node.getPort());

		InternalRequest.DeadNodeRequest DNRequest = InternalRequest.DeadNodeRequest.newBuilder()
				.setServer(node.getAddress().getHostName())
				.setPort(node.getPort())
				.build();

		// Create epidemic containing DNRequest
		Epidemic epi = new Epidemic(DNRequest.toByteString(), 2);
		epi.generateID(node.getAddress(), node.getEpiPort());

		//  Spread the epidemic across nodes
		Server.EpidemicServer.add(epi);
    }

    public void run() {
		if (FIRST_TIME_FLAG) {
			FIRST_TIME_FLAG = false;

			try {
				Thread.sleep(2*60*1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}


		while (!STOP_FLAG) {
			try {
				checkRandom();
				Thread.sleep(5000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
    }

    public void start() {
        if (t != null) return;

        t = new Thread(this);
		t.setPriority(Thread.MAX_PRIORITY);
		t.start();
    }

    public void stop() {
		STOP_FLAG = true;
	}
}
