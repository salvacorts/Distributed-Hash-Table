package com.g9A.CPEN431.A7.server.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import com.g9A.CPEN431.A7.server.Server;
import com.g9A.CPEN431.A7.server.Worker;
import com.google.protobuf.InvalidProtocolBufferException;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.Message;

public class EpidemicServer implements Runnable {
	
	private static boolean KEEP_RECEIVING = true;

	private final EpidemicCache Cache = EpidemicCache.getInstance();
	private DatagramSocket listeningSocket;
	private Thread t;

	public EpidemicServer(int port) throws SocketException{
        this.listeningSocket = new DatagramSocket(port);
	}

	public static InternalRequest.DeadNodeRequest UnpackDNRequest(Message.Msg msg) throws InvalidProtocolBufferException{
		return InternalRequest.DeadNodeRequest.newBuilder().mergeFrom(msg.getPayload()).build();
	}
	
	public void add(Epidemic epi) {
		if (!Cache.check(epi.getID())) {
			Cache.put(epi.getID());
			epi.start();
		}
	}
	
	public void run() {
		while (KEEP_RECEIVING) {
			byte[] receiveData = new byte[65507];
			DatagramPacket rec_packet = new DatagramPacket(receiveData, receiveData.length);

            try {
                // Receive a packet
                listeningSocket.receive(rec_packet);

                // Deserialize packet
                Message.Msg rec_msg = Worker.UnpackMessage(rec_packet);
                InternalRequest.DeadNodeRequest request = EpidemicServer.UnpackDNRequest(rec_msg);

                // Remove the node that is down
        		Server.removeNode(request.getServer(), request.getPort());

                // Spread the epidemic
        		InternalRequest.DeadNodeRequest DNRequest = InternalRequest.DeadNodeRequest.newBuilder()
        				.setServer(request.getServer())
        				.setPort(request.getPort())
        				.build();

        		Epidemic epi = new Epidemic(DNRequest.toByteString(), 2, rec_msg.getEpidemic().getId());
        		this.add(epi);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}
	
	public void start() {
		KEEP_RECEIVING = true;

        if (t == null) {
            t = new Thread(this);
            t.setPriority(Thread.MAX_PRIORITY);
            t.start();
        }
	}
	
	public void stop() {
		KEEP_RECEIVING = false;
	}
}
