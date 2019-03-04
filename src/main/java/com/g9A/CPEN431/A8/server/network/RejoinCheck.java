package com.g9A.CPEN431.A8.server.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

import com.g9A.CPEN431.A8.client.Client;
import com.g9A.CPEN431.A8.server.Server;
import com.g9A.CPEN431.A8.server.ServerNode;
import com.g9A.CPEN431.A8.server.Worker;
import com.g9A.CPEN431.A8.server.exceptions.InvalidHashRangeException;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.InternalRequest.EpidemicRequest.EpidemicType;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

public class RejoinCheck implements Runnable{

	private static boolean FIRST_TIME_FLAG = true;
	private static boolean STOP_FLAG = false;
    private DatagramSocket socket;
	private Thread t;
	
	private List<ServerNode> deadNodes;

	public RejoinCheck() throws SocketException {
		socket = new DatagramSocket();
		deadNodes = Collections.synchronizedList(new ArrayList<ServerNode>());
	}
	
	/**
	 * Adds a dead node to ping for alive status
	 * @param node the dead node
	 */
	public void addDeadNode(ServerNode node) {
		if (!deadNodes.contains(node)) deadNodes.add(node);
	}
	
	/**
	 * Removes a dead node
	 * @param addr address of the node
	 * @param port port of the node
	 * @throws UnknownHostException 
	 */
	public ServerNode removeDeadNode(String addr, int port) throws UnknownHostException {
		InetAddress address = InetAddress.getByName(addr);

		for (Iterator<ServerNode> iter = deadNodes.iterator(); iter.hasNext();) {
			ServerNode node = iter.next();

			if (node.getAddress().equals(address) && node.getPort() == port) {
				iter.remove();
				return node;
			}
		}

		return null;
	}
	
	/**
	 * Re-adds a previously dead node to the list. Starts an alive node epidemic
	 * @param node the node to rejoin
	 * @throws SocketException 
	 * @throws InvalidHashRangeException 
	 * @throws UnknownHostException 
	 */
	private void rejoinNode(ServerNode node) throws SocketException, UnknownHostException, InvalidHashRangeException, IOException {
		// Update nodes lists calculating this node hashspace
		deadNodes.remove(node);
		Server.RejoinNode(node);

		// Spread an epidemic with this information
		ByteString id = Epidemic.generateID(node.getAddress(), node.getEpiPort());

		InternalRequest.EpidemicRequest epiRequest = InternalRequest.EpidemicRequest.newBuilder()
					.setServer(node.getAddress().getHostName())
					.setPort(node.getPort())
					.setEpId(id)
					.setType(EpidemicType.ALIVE)
					.setHashEnd(node.getHashEnd())
					.setHashStart(node.getHashStart())
				.build();

		// Create epidemic containing DNRequest
		Epidemic epi = new Epidemic(epiRequest.toByteString(), id);

		//  Spread the epidemic across nodes
		Server.EpidemicServer.add(epi);
	}
	
	/**
	 * Check if a random dead node is alive
	 * @throws SocketException
	 * @throws InvalidHashRangeException 
	 */
	private void checkRandom() throws SocketException, InvalidHashRangeException {
		// If there is only one node (this one), return, there is nothing to check
    	if (this.deadNodes.size() < 1) return;

    	// Randomly select a node to check
		ServerNode node;
		Random rand = new Random();

		do {
        	int r = rand.nextInt(this.deadNodes.size());
        	node = this.deadNodes.get(r);
    	} while(node.equals(Server.selfNode));

    	try {
			// Send isAlive to the node
			ByteString uuid = Worker.GetUUID(socket);
			KeyValueRequest.KVRequest request = KeyValueRequest.KVRequest.newBuilder().setCommand(6).build();
			Message.Msg msg = Client.PackMessage(request, uuid.toByteArray(), 1);

			// Serialize to packet
			DatagramPacket send_packet = new DatagramPacket(msg.toByteArray(), msg.getSerializedSize(), node.getAddress(), node.getPort());

			// Send packet
			KVResponse kvr = Worker.SendAndReceive(socket, send_packet, uuid, 5);

			// if node is alive again
			if (kvr.getErrCode() == 0) rejoinNode(node);

		} catch (IOException ignore) {}
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
		STOP_FLAG = false;

        if (t == null) {
            t = new Thread(this);
            t.setPriority(Thread.MAX_PRIORITY);
            t.start();
        }
	}
	
	public void stop() {
		STOP_FLAG = true;
	}
}
