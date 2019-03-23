package com.g9A.CPEN431.A11.server.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Random;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;

import com.g9A.CPEN431.A11.client.Client;
import com.g9A.CPEN431.A11.server.Server;
import com.g9A.CPEN431.A11.server.ServerNode;
import com.g9A.CPEN431.A11.server.Worker;
import com.g9A.CPEN431.A11.server.metrics.MetricsServer;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.InternalRequest.EpidemicRequest.EpidemicType;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

public class FailureCheck implements Runnable {
	private static boolean FIRST_TIME_FLAG = true;
	private static boolean STOP_FLAG = false;
    private DatagramSocket socket;
	private Thread t;
	
    private final MetricsServer metrics = MetricsServer.getInstance();

    public FailureCheck() throws SocketException {
    	this.socket = new DatagramSocket();
    }

	/**
	 * Check if a random node is dead
	 * @throws InvalidHashRangeException 
	 * @throws IOException 
	 */
	private void checkRandom() throws IOException {
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
			Message.Msg msg = Client.PackMessage(request, uuid.toByteArray(), 3);

			// Serialize to packet
			DatagramPacket send_packet = new DatagramPacket(msg.toByteArray(), msg.getSerializedSize(), node.getAddress(), node.getEpiPort());

			// Send packet
			KVResponse kvr = Worker.SendAndReceive(socket, send_packet, uuid, 4);

			// if sth went wrong
			if (kvr.getErrCode() != 0) {
				removeNode(node);
			}

		} catch (IOException e) {	// If could not establish contact with the node, remove the node
			System.out.println("[FailureCheck] Server " + node.getAddress().getHostName() + ":" + node.getPort() + " is down");
			removeNode(node);
		}
    }
    
	/**
	 * Removes the dead node from the current Server and spreads a dead node epidemic
	 * @param node the dead node
	 * @throws IOException 
	 * @throws InvalidHashRangeException 
	 */
    public void removeNode(ServerNode node) throws IOException {
		Server.RemoveNode(node.getId());

		ByteString id = Epidemic.generateID(node.getAddress(), node.getEpiPort(), EpidemicType.DEAD);
		
		long timestamp = System.currentTimeMillis()/1000L;
		
		InternalRequest.ServerNode serverNode = InternalRequest.ServerNode.newBuilder()
				.setServer(node.getAddress().getHostAddress())
				.setPort(node.getPort())
				.setNodeId(node.getId())
				.build();
		InternalRequest.EpidemicRequest epiRequest = InternalRequest.EpidemicRequest.newBuilder()
				.setServerNode(serverNode)
				.setEpId(id)
				.setTimestamp(timestamp)
				.setType(EpidemicType.DEAD)
				.build();
		
		metrics.deadEpidemics.inc();

		// Create epidemic containing epiRequest
		Epidemic epi = new Epidemic(epiRequest);

		//  Spread the epidemic across nodes
		Server.epidemicServer.add(epi);
    }

    public void run() {
		if (FIRST_TIME_FLAG) {
			FIRST_TIME_FLAG = false;

			try {
				Thread.sleep(1*60*1000);
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
    
    public void restart() {
    	stop();
    	FIRST_TIME_FLAG = true;
    	t.stop();
    	t = null;
    	start();
    }

    public void start() {
        if (t != null) return;

        t = new Thread(this);
		t.start();
    }

    public void stop() {
		STOP_FLAG = true;
		FIRST_TIME_FLAG = true;
	}
}
