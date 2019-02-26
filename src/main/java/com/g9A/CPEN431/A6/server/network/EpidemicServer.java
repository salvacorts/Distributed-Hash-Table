package com.g9A.CPEN431.A6.server.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import com.g9A.CPEN431.A6.server.Server;
import com.g9A.CPEN431.A6.server.ServerNode;
import com.g9A.CPEN431.A6.server.Worker;
import com.g9A.CPEN431.A6.server.pools.SocketFactory;
import com.g9A.CPEN431.A6.server.pools.SocketPool;
import com.google.protobuf.InvalidProtocolBufferException;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.Message;

public class EpidemicServer implements Runnable {
	
	static boolean KEEP_RECEIVING = true;
	int port;
	private Thread t;
	
	private DatagramSocket listeningSocket;
    private static EpidemicCache cache = EpidemicCache.getInstance();
	
	public EpidemicServer(int port) throws SocketException{
		this.port = port;
        this.listeningSocket = new DatagramSocket(port);
	}
	
	public void add(Epidemic epi) {
		if (!cache.check(epi.epId)) {
			epi.start();
			cache.put(epi.epId);
		}
	}
    
    private static InternalRequest.DeadNodeRequest UnpackDNRequest(Message.Msg msg) throws InvalidProtocolBufferException{
        return InternalRequest.DeadNodeRequest.newBuilder().mergeFrom(msg.getPayload()).build();

    }
	
	public void run() {
        byte[] receiveData = new byte[65507];
        
		while (KEEP_RECEIVING) {
            DatagramPacket rec_packet = new DatagramPacket(receiveData, receiveData.length);

            try {
                // Receive a packet
                listeningSocket.receive(rec_packet);

                // Deserialize packet
                Message.Msg rec_msg = Worker.UnpackMessage(rec_packet);
                InternalRequest.DeadNodeRequest request = UnpackDNRequest(rec_msg);

                // Remove the node that is down
        		Server.removeNode(request.getServer(), request.getPort());

                // Spread the epidemic
        		InternalRequest.DeadNodeRequest DNRequest = InternalRequest.DeadNodeRequest.newBuilder()
        				.setServer(request.getServer())
        				.setPort(request.getPort())
        				.build();

        		Epidemic epi = new Epidemic(DNRequest.toByteString(), 2, rec_msg.getEpID());
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
            t.start();
        }
	}
	
	public void stop() {
		KEEP_RECEIVING = false;
	}
}
