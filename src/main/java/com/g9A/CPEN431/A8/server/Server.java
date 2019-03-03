package com.g9A.CPEN431.A8.server;

import java.io.IOException;

// TODO: CATCH com.google.protobuf.InvalidProtocolBufferException: While parsing a protocol message, the input ended unexpectedly in the middle of a field.  This could mean either than the input has been truncated or that an embedded message misreported its own length. PROPERLY
// TODO: Calc if PUT will be successful based on heap size and used size.

import java.net.*;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.g9A.CPEN431.A8.server.exceptions.InvalidHashRangeException;
import com.g9A.CPEN431.A8.server.kvMap.KVMapKey;
import com.g9A.CPEN431.A8.server.kvMap.KVMapValue;
import com.g9A.CPEN431.A8.server.kvMap.RequestProcessor;
import com.g9A.CPEN431.A8.server.network.EpidemicServer;
import com.g9A.CPEN431.A8.server.network.FailureCheck;
import com.g9A.CPEN431.A8.server.network.RejoinCheck;
import com.g9A.CPEN431.A8.server.pools.SocketFactory;
import com.g9A.CPEN431.A8.server.pools.SocketPool;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.InternalRequest.KVTransfer;

public class Server {
    static boolean KEEP_RECEIVING = true;
    static long avgProcessTime = 0;

    private DatagramSocket listeningSocket;        // Server UDP socket to send and receive packets
    private int availableCores;

    public static ExecutorService threadPool;
    public static SocketPool socketPool = new SocketPool(new SocketFactory());
    public static ServerNode selfNode;
    public static List<ServerNode> ServerNodes;
    public static EpidemicServer EpidemicServer;
    public static FailureCheck FailureCheck;
    public static RejoinCheck RejoinCheck;
    
    private static RequestProcessor requestProcessor = RequestProcessor.getInstance();

    static void UpdateProcessTime(long time) {
        avgProcessTime = (avgProcessTime + time) / 2;
    }

    public Server(int port, int epiPort, List<ServerNode> otherNodes) throws Exception {
        this.listeningSocket = new DatagramSocket(port);
        this.availableCores = Runtime.getRuntime().availableProcessors();

        int poolSize = (this.availableCores > 1) ? this.availableCores - 1 : 1;

        // Setup threads pool
        if (threadPool != null && !threadPool.isShutdown()) threadPool.shutdownNow();
        threadPool = Executors.newFixedThreadPool(poolSize);

        // Setup sockets pool
        socketPool.setMaxTotal(25);
        socketPool.setMinIdle(poolSize);

        ServerNodes = otherNodes;

        InetAddress local = InetAddress.getLocalHost();

        // Initialize additional services
        EpidemicServer = new EpidemicServer(epiPort);
        FailureCheck = new FailureCheck();

        for (ServerNode node : ServerNodes) {
            if (port == node.getPort() && (local.equals(node.getAddress())
                                            || node.getAddress().getHostAddress().equals("127.0.0.1"))) {
                selfNode = node;
                break;
            }
        }

        if (selfNode == null) {
        	throw new IllegalArgumentException("Current server not present in nodes-list");
        }
    }

    public static void removeNode(String addr, int port) {
        // Remove the node from the nodes list
    	for (Iterator<ServerNode> iter = ServerNodes.listIterator(); iter.hasNext(); ) {
    		ServerNode node = iter.next();

    	    if (addr.equals(node.getAddress().getHostAddress()) && node.getPort() == port) {
    	    	
            	// Re-balance hash space
            	if (!iter.hasNext()) {
            		ServerNode lastNode = ServerNodes.get(ServerNodes.size()-2);
            		lastNode.setHashRange(lastNode.getHashStart(), node.getHashEnd());
            	} else {
        	        ServerNode nextNode = iter.next();
            		nextNode.setHashRange(node.getHashStart(), nextNode.getHashEnd());
            	}

            	RejoinCheck.addDeadNode(node);
    	        ServerNodes.remove(node);
    	        

    	    	/*for (int i = 0; i < ServerNodes.size(); i++) {

    	    		//ServerNodes.get(i).setHashRange(start, end);
    	    		node = ServerNodes.get(i);
    	    		System.out.println(node.getAddress().getHostName() + ":" + node.getPort() + ", Range: " + node.getHashStart() + "-" + node.getHashEnd());
    	    	}*/
    	        
    	        return;
    	    }
    	}
    }
    
    /**
     * Re-adds a node into the ServerNodes list. New hash space undetermined
     * @param node the node to rejoin
     * @throws UnknownHostException
     * @throws InvalidHashRangeException
     */
    public static void rejoinNode(ServerNode node) throws UnknownHostException, InvalidHashRangeException {
        // Add the node to the nodes list
    	int maxSize = 0;
    	ServerNode maxNode = null;
    	for (Iterator<ServerNode> iter = ServerNodes.listIterator(); iter.hasNext(); ) {
    		
    		ServerNode n = iter.next();
    		int hashSize = n.getHashEnd() - n.getHashStart();
    		if(hashSize > maxSize) {
    			maxSize = hashSize;
    			maxNode = node;
    		}

    	    //TODO: rebalance hash space
    	}
    	int end = maxNode.getHashEnd();
    	int hashSize = maxNode.getHashEnd() - maxNode.getHashStart();
    	int start = end - hashSize/2;
    	maxNode.setHashRange(maxNode.getHashStart(), start-1);
    	node.setHashRange(start, end);
    	ServerNodes.add(node);
    	
    	/*for (int i = 0; i < ServerNodes.size(); i++) {

		//ServerNodes.get(i).setHashRange(start, end);
		node = ServerNodes.get(i);
		System.out.println(node.getAddress().getHostName() + ":" + node.getPort() + ", Range: " + node.getHashStart() + "-" + node.getHashEnd());
		}*/
    }
    
    /**
     * Re-adds a node into the ServerNodes list. New hash space already defined
     * @param node the node to rejoin
     * @throws InvalidHashRangeException
     * @throws IOException 
     */
    public static void rejoinNode(String addr, int port, int hashStart, int hashEnd) throws InvalidHashRangeException, IOException {
        // Add the node to the nodes list and remove from dead nodes list
    	ServerNode node = RejoinCheck.removeDeadNode(addr, port);
    	ServerNodes.add(node);
    	
    	//Transfer hash space
    	for (Iterator<ServerNode> iter = ServerNodes.listIterator(); iter.hasNext(); ) {
    		ServerNode n = iter.next();
    		if(n.getHashEnd() >= hashStart && n.getHashStart() <= hashEnd ) {
        		n.setHashRange(n.getHashStart(), hashStart-1);
        		
            	//If new node is taking over this node's hashspace, transfer keys
            	if(n.equals(selfNode)) {
            		TransferKeys(addr, port, hashStart, hashEnd);
            	}
        	}
    	}
    }
    
    private static void TransferKeys(String addr, int port, int hashStart, int hashEnd) throws UnknownHostException, IOException {
    	InternalRequest.KVTransfer.Builder builder = InternalRequest.KVTransfer.newBuilder();
    	Map<ByteString, KVMapValue> map = requestProcessor.MassGet(hashStart, hashEnd);
    	map.forEach((k,v) -> {
    		ByteString value = ByteString.copyFrom(v.getValue());
    		InternalRequest.KVPair pair = InternalRequest.KVPair.newBuilder()
    				.setKey(k)
    				.setValue(value)
    				.setVersion(v.getVersion())
    				.build();
        	builder.addKvlist(pair);
    	});
    	InternalRequest.KVTransfer request = builder.build();
    	DatagramSocket socket = new DatagramSocket();
    	ByteString uuid = Worker.GetUUID(socket);
    	Message.Msg msg = Worker.PackMessage(request, uuid);
        DatagramPacket packet = new DatagramPacket(msg.toByteArray(), msg.getSerializedSize(), InetAddress.getByName(addr), port);
    	try{
    		Worker.SendAndReceive(socket, packet, uuid, 3);
    	}
    	catch(SocketTimeoutException e) {
    		System.err.println("Transfer key error");
    		e.printStackTrace();
        	socket.close();
        	return;
    	}
    	requestProcessor.MassDelete(hashStart, hashEnd);
    	socket.close();
    }

    public static void LaunchWorkerWithPriority(DatagramPacket packet, int priority) {
        threadPool.execute(new Worker(packet, priority));
    }

    public void StartServing() {
        System.out.println("Listening on: " + this.listeningSocket.getLocalPort());
        System.out.println("CPUs: " + this.availableCores);

        // Launch the epidemic service to update nodes state across the ring
        EpidemicServer.start();

        // Launch the FailureCheck and RejoinCheck threads
        FailureCheck.start();
        RejoinCheck.start();

        while (KEEP_RECEIVING) {
            byte[] receiveData = new byte[20000];
            DatagramPacket rec_packet = new DatagramPacket(receiveData, receiveData.length);

            try {
                // Receive a packet
                listeningSocket.receive(rec_packet);

                // Launch a new worker on the pool
                threadPool.execute(new Worker(rec_packet));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("Stopping server");
        this.listeningSocket.close();
        threadPool.shutdown();
        socketPool.close();
        EpidemicServer.stop();
        FailureCheck.stop();
        RejoinCheck.stop();
    }
}


