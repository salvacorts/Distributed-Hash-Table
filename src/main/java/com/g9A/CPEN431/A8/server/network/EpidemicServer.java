package com.g9A.CPEN431.A8.server.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import com.g9A.CPEN431.A8.client.Client;
import com.g9A.CPEN431.A8.server.HashSpace;
import com.g9A.CPEN431.A8.server.Server;
import com.g9A.CPEN431.A8.server.Worker;
import com.g9A.CPEN431.A8.server.exceptions.InvalidHashRangeException;
import com.g9A.CPEN431.A8.server.metrics.MetricsServer;
import com.google.protobuf.InvalidProtocolBufferException;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.Message;

public class EpidemicServer implements Runnable {
	
	private static boolean KEEP_RECEIVING = true;

	private final EpidemicCache Cache = EpidemicCache.getInstance();
	private DatagramSocket listeningSocket;
	private Thread t;
	private final MetricsServer metrics = MetricsServer.getInstance();

	public EpidemicServer(int port) throws SocketException {
        this.listeningSocket = new DatagramSocket(port);
	}

	public static InternalRequest.EpidemicRequest UnpackEpidemicRequest(Message.Msg msg) throws InvalidProtocolBufferException{
		return InternalRequest.EpidemicRequest.newBuilder().mergeFrom(msg.getPayload()).build();
	}
	
	public void add(Epidemic epi) throws InvalidHashRangeException, IOException {
		if (Cache.check(epi.getID())) return;

		Cache.put(epi.getID());
		epi.start();
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
					return;
				}

                InternalRequest.EpidemicRequest request = EpidemicServer.UnpackEpidemicRequest(rec_msg);

                metrics.epidemicMessagesReceieved.inc();

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
