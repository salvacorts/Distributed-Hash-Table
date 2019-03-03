package com.g9A.CPEN431.A8.server.network;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;
import java.util.zip.CRC32;

import ca.NetSysLab.ProtocolBuffers.InternalRequest.EpidemicRequest.EpidemicType;
import ca.NetSysLab.ProtocolBuffers.Message;

import com.g9A.CPEN431.A8.server.Server;
import com.g9A.CPEN431.A8.server.ServerNode;
import com.g9A.CPEN431.A8.server.Worker;
import com.g9A.CPEN431.A8.utils.ByteOrder;
import com.google.protobuf.ByteString;

public class Epidemic implements Runnable {

    private static boolean STOP_FLAG = false;

	private ByteString epId = null;
	private ByteString payload;
	private DatagramSocket socket;
	private int iterations;
	private Thread t;

    public Epidemic(ByteString payload, ByteString epId) throws java.net.SocketException {
    	this.socket = new DatagramSocket();
    	this.payload = payload;
    	iterations = Server.ServerNodes.size();

    	if (iterations < 10) iterations = (10 - iterations) * 2;
    }

	public static ByteString generateID(InetAddress svr, int port) {
		Random randomGen = new Random();
		byte[] buffUuid = new byte[16];

		byte[] addr = svr.getAddress();
		short rnd = (short) randomGen.nextInt(Short.MAX_VALUE + 1);
		long timestamp = System.nanoTime();

		System.arraycopy(addr, 0, buffUuid, 0, 4);
		ByteOrder.int2leb(port, buffUuid, 4);
		ByteOrder.short2leb(rnd, buffUuid, 6);
		ByteOrder.long2leb(timestamp, buffUuid, 8);

		return ByteString.copyFrom(buffUuid);
	}

	private static Message.Msg PackInternalMessage(ByteString payload, DatagramSocket socket) {
		CRC32 crc32 = new CRC32();
		ByteString uuid = Worker.GetUUID(socket);

		byte[] concat = ByteOrder.concatArray(uuid.toByteArray(), payload.toByteArray());
		crc32.update(concat);

		return Message.Msg.newBuilder()
					.setMessageID(Worker.GetUUID(socket))
					.setCheckSum(crc32.getValue())
					.setPayload(payload)
				.build();
	}
    
    private void sendRandom() throws IOException {

    	// If there is only one node (this one) there is nothing to spread
    	if (Server.ServerNodes.size() < 2) {
			STOP_FLAG = true;
    		return;
		}

    	// Pick a node randomly
    	ServerNode node;
		Random rand = new Random();

		do {
	    	int r = rand.nextInt(Server.ServerNodes.size());
	    	node = Server.ServerNodes.get(r);
    	} while(node.equals(Server.selfNode));

		// Pack internal message
		Message.Msg msg = PackInternalMessage(payload, socket);

		// Send the payload to that node
		System.out.println("[Epidemic] sending to " + node.getAddress().getHostName() + ":" + node.getEpiPort());
		Worker.Send(socket, msg, node.getAddress(), node.getEpiPort());

    	iterations--;
    }
    
    public void run() {

    	while (!STOP_FLAG && iterations > 0) {
			try {
				sendRandom();
				Thread.sleep(5000);
			} catch (IOException e) {
				System.out.println("Epidemic failure");
				e.printStackTrace();
			} catch (InterruptedException e) {
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

	public ByteString getID() {
		return epId;
	}

    @Override
    public boolean equals(Object other) {
    	if (other instanceof Epidemic) return this.epId == ((Epidemic) other).epId;

    	return this.epId.equals(other);
	}
}
