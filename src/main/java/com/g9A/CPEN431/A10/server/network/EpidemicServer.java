package com.g9A.CPEN431.A10.server.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;

import com.g9A.CPEN431.A10.client.Client;
import com.g9A.CPEN431.A10.server.HashSpace;
import com.g9A.CPEN431.A10.server.Server;
import com.g9A.CPEN431.A10.server.Worker;
import com.g9A.CPEN431.A10.server.exceptions.InvalidHashRangeException;
import com.g9A.CPEN431.A10.server.metrics.MetricsServer;
import com.google.protobuf.InvalidProtocolBufferException;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.Message;

public class EpidemicServer implements Runnable {
	
	private static boolean KEEP_RECEIVING = true;

	private static final EpidemicCache Cache = EpidemicCache.getInstance();
	private static List<Epidemic> epidemics; 
	private DatagramSocket listeningSocket;
	private Thread t;
	private final MetricsServer metrics = MetricsServer.getInstance();

	public EpidemicServer(int port) throws SocketException {
        this.listeningSocket = new DatagramSocket(port);
        epidemics = new ArrayList<Epidemic>();
	}

	public static InternalRequest.EpidemicRequest UnpackEpidemicRequest(Message.Msg msg) throws InvalidProtocolBufferException{
		return InternalRequest.EpidemicRequest.newBuilder().mergeFrom(msg.getPayload()).build();
	}
	
	public static void add(Epidemic epi) throws InvalidHashRangeException, IOException {
		if (Cache.check(epi.getID())) return;

		Cache.put(epi.getID());
		epidemics.add(epi);
		epi.start();
	}
	
	public static void remove(Epidemic epi) {
		epidemics.remove(epi);
	}
	
	public static void clear() {
		for(Epidemic epi: epidemics) {
			epi.stop();
		}
		epidemics = new ArrayList<Epidemic>();
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

                // if internal isAlive, answer
                if (rec_msg.getType() == 3) {
					KeyValueResponse.KVResponse response = KeyValueResponse.KVResponse.newBuilder().setErrCode(0).build();
					Message.Msg msg = Worker.PackMessage(response, rec_msg.getMessageID());

					Worker.Send(listeningSocket, msg, rec_packet.getAddress(), rec_packet.getPort());
					continue;
				}

                InternalRequest.EpidemicRequest request = EpidemicServer.UnpackEpidemicRequest(rec_msg);
                long now = System.currentTimeMillis() / 1000L;
                if(now - request.getTimestamp() >= 60) {
                	continue;
                }

                // Spread the epidemic
        		Epidemic epi = new Epidemic(request);
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
