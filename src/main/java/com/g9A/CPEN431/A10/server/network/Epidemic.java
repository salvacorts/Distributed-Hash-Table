package com.g9A.CPEN431.A10.server.network;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.zip.CRC32;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.InternalRequest.EpidemicRequest;
import ca.NetSysLab.ProtocolBuffers.InternalRequest.EpidemicRequest.EpidemicType;
import ca.NetSysLab.ProtocolBuffers.Message;

import com.g9A.CPEN431.A10.server.HashSpace;
import com.g9A.CPEN431.A10.server.Server;
import com.g9A.CPEN431.A10.server.ServerNode;
import com.g9A.CPEN431.A10.server.Worker;
import com.g9A.CPEN431.A10.server.metrics.MetricsServer;
import com.g9A.CPEN431.A10.utils.ByteOrder;
import com.g9A.CPEN431.A10.utils.StringUtils;
import com.google.protobuf.ByteString;

public class Epidemic implements Runnable {

    private static boolean STOP_FLAG = false;

	private ByteString epId = null;
	private ByteString payload;
	private EpidemicRequest request;
	private DatagramSocket socket;
	private int iterations;
	private Thread t;
	
	private MetricsServer metrics = MetricsServer.getInstance();

    public Epidemic(EpidemicRequest request) throws java.net.SocketException {
    	this.socket = new DatagramSocket();
    	this.request = request;
    	iterations = Server.ServerNodes.size();
    	this.epId = request.getEpId();
    	this.payload = request.toByteString();

    	//if (iterations < 100) iterations += 5;
    	if(iterations < 20) iterations += 10;
    	iterations *= 2;
    }

	public static ByteString generateID(InetAddress svr, int port, EpidemicType type) {
		Random randomGen = new Random();
		byte[] buffUuid = new byte[16];

		byte[] addr = svr.getAddress();
		//short rnd = (short) randomGen.nextInt(Short.MAX_VALUE + 1);
		long timestamp = System.nanoTime();

		System.arraycopy(addr, 0, buffUuid, 0, 4);
		ByteOrder.int2leb(port, buffUuid, 4);
		
		switch(type) {
		case DEAD:
			ByteOrder.int2leb(EpidemicType.DEAD_VALUE, buffUuid, 6);
			break;
		case ALIVE:
			ByteOrder.int2leb(EpidemicType.ALIVE_VALUE, buffUuid, 6);
			break;
		}
		//ByteOrder.short2leb(rnd, buffUuid, 6);
		ByteOrder.long2leb(timestamp, buffUuid, 8);

		return ByteString.copyFrom(buffUuid);
	}

	public static Message.Msg PackInternalMessage(ByteString payload, DatagramSocket socket) {
		CRC32 crc32 = new CRC32();
		ByteString uuid = Worker.GetUUID(socket);

		byte[] concat = ByteOrder.concatArray(uuid.toByteArray(), payload.toByteArray());
		crc32.update(concat);

		return Message.Msg.newBuilder()
					.setMessageID(uuid)
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
		//System.out.println("[Epidemic " + StringUtils.byteArrayToHexString(this.epId.toByteArray()) + "] sending to " + node.getAddress().getHostName() + ":" + node.getEpiPort());
		Worker.Send(socket, msg, node.getAddress(), node.getEpiPort());

    	iterations--;
    }
    
    public void run() {
		InternalRequest.ServerNode node;
    	
    	switch (request.getType()) {
			case DEAD:	// Remove the node that is down
				node = request.getServerNode();
                Server.RemoveNode(node.getNodeId());
				metrics.deadMessagesReceieved.inc();
				break;
			case ALIVE:	// Re-add the node that was down
				try {
					node = request.getServerNode();
					Server.RejoinNode(node.getNodeId(), node.getServer(), node.getPort(), node.getHashesList());
					
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				metrics.aliveMessagesReceieved.inc();
				break;
			case STATE: // Transfer the system state to restarted node
				Server.ReceiveState(request.getState());
				STOP_FLAG = true;
				iterations = 0;
				break;
	    }

    	while (!STOP_FLAG && iterations > 0) {
			try {
				sendRandom();
				Thread.sleep(100);
			} catch (IOException e) {
				System.out.println("Epidemic failure");
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
    	EpidemicServer.remove(this);
    	socket.close();
    }

    public void start() throws IOException {
    	
    	if (this.epId == null) {
    		System.err.println("Missing epidemic ID!");
    	}

		STOP_FLAG = false;

        if (t == null) {
            t = new Thread(this);
            t.setPriority(Thread.MAX_PRIORITY);
            t.start();
        }
    }

    public void stop() {
    	t.interrupt();
    	EpidemicServer.remove(this);
		STOP_FLAG = true;
    	socket.close();
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
