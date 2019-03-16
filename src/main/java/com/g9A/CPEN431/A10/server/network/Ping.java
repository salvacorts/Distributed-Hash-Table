package com.g9A.CPEN431.A10.server.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import com.g9A.CPEN431.A10.client.Client;
import com.g9A.CPEN431.A10.server.Server;
import com.g9A.CPEN431.A10.server.ServerNode;
import com.g9A.CPEN431.A10.server.Worker;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

public class Ping implements Runnable {
	
	private ServerNode node;
	
	public Ping(ServerNode node) {
		this.node = node;
	}

	public void run() {
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		ByteString uuid = Worker.GetUUID(socket);
		KeyValueRequest.KVRequest request = KeyValueRequest.KVRequest.newBuilder().setCommand(6).build();
		Message.Msg msg = Client.PackMessage(request, uuid.toByteArray(), 3);

		// Serialize to packet
		DatagramPacket send_packet = new DatagramPacket(msg.toByteArray(), msg.getSerializedSize(), node.getAddress(), node.getEpiPort());

		// Send packet
		try {
			KVResponse kvr = Worker.SendAndReceive(socket, send_packet, uuid, 3);
			if (kvr.getErrCode() != 0) {
				Server.RemoveNode(node.getId());
			}
			else {
				Server.RejoinNode(node.getId(), node.getAddress().getHostAddress(), node.getPort(), node.getHashValues());
			}
		} catch (IOException e) {
			Server.RemoveNode(node.getId());
		}
		socket.close();
	}
}
