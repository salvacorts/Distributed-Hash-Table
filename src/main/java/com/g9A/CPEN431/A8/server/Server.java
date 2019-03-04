package com.g9A.CPEN431.A8.server;

import java.io.IOException;

// TODO: CATCH com.google.protobuf.InvalidProtocolBufferException: While parsing a protocol message, the input ended unexpectedly in the middle of a field.  This could mean either than the input has been truncated or that an embedded message misreported its own length. PROPERLY
// TODO: Calc if PUT will be successful based on heap size and used size.

import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import com.g9A.CPEN431.A8.client.Client;
import com.g9A.CPEN431.A8.server.exceptions.InvalidHashRangeException;
import com.g9A.CPEN431.A8.server.kvMap.KVMapKey;
import com.g9A.CPEN431.A8.server.kvMap.KVMapValue;
import com.g9A.CPEN431.A8.server.kvMap.RequestProcessor;
import com.g9A.CPEN431.A8.server.network.Epidemic;
import com.g9A.CPEN431.A8.server.network.EpidemicServer;
import com.g9A.CPEN431.A8.server.network.FailureCheck;
import com.g9A.CPEN431.A8.server.pools.SocketFactory;
import com.g9A.CPEN431.A8.server.pools.SocketPool;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.InternalRequest.KVTransfer;
import ca.NetSysLab.ProtocolBuffers.InternalRequest.EpidemicRequest.EpidemicType;

public class Server {
    static boolean KEEP_RECEIVING = true;
    static long avgProcessTime = 0;

    private DatagramSocket listeningSocket;        // Server UDP socket to send and receive packets
    private int availableCores;

    public static ExecutorService threadPool;
    public static SocketPool socketPool = new SocketPool(new SocketFactory());
    public static ServerNode selfNode;
    public static List<ServerNode> ServerNodes;
	private static List<ServerNode> DeadNodes;
    public static EpidemicServer EpidemicServer;
    public static FailureCheck FailureCheck;
    
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
        DeadNodes = Collections.synchronizedList(new ArrayList<ServerNode>());

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
    
    /**
     * Notify other nodes this one is online (epidemic protocol)
     * @throws SocketException 
     */
    public static void AlertOtherNodes() throws SocketException {
		ByteString id = Epidemic.generateID(selfNode.getAddress(), selfNode.getEpiPort(), EpidemicType.ALIVE);
		HashSpace space = selfNode.getHashSpaces().get(0);
    	InternalRequest.EpidemicRequest epiRequest = InternalRequest.EpidemicRequest.newBuilder()
				.setServer(selfNode.getAddress().getHostAddress())
				.setPort(selfNode.getPort())
				.setEpId(id)
				.setType(EpidemicType.ALIVE)
				.setHashStart(space.hashStart)
				.setHashEnd(space.hashEnd)
			.build();

		Epidemic epi = new Epidemic(epiRequest.toByteString(), id);
		epi.start();
    }

    public static void RemoveNode(String addr, int port) {
        // Remove the node from the nodes list
    	for (ListIterator<ServerNode> iter = ServerNodes.listIterator(); iter.hasNext(); ) {
    		ServerNode node = iter.next();

      	    if (addr.equals(node.getAddress().getHostAddress()) && node.getPort() == port) {

                DeadNodes.add(node);
                iter.remove();

            	// Re-balance hash space
            	if (!iter.hasNext()) {
            		ServerNode lastNode = ServerNodes.get(ServerNodes.size()-2);
            		lastNode.addHashSpaces(node.getHashSpaces());
            	} else {
        	        ServerNode nextNode = iter.next();
            		nextNode.addHashSpaces(node.getHashSpaces());
            	}

    	    	/*for (ServerNode n : ServerNodes) {

    	    		System.out.println(n.getAddress().getHostName() + ":" + n.getPort() + ", Range: " + n.getHashSpaces().get(0).toString());

    	    		for (HashSpace hs : n.getHashSpaces()) {
    	    			System.out.println("& " + hs.toString());
    	    		}
    	    	}*/
    	        
    	        return;
    	    }
    	}
    }
    
    // Determines if the server has a node currently in DeadNodes, to avoid unnecessary epidemics
    public static boolean HasDeadNode(String addr, int port) throws UnknownHostException {
    	InetAddress address = InetAddress.getByName(addr);
    	for (Iterator<ServerNode> iter = DeadNodes.iterator(); iter.hasNext();) {
			ServerNode n = iter.next();

			if (n.getAddress().equals(address) && n.getPort() == port) {
				return true;
			}
		}
    	return false;
    }
    
    /**
     * Re-adds a node into the ServerNodes list. New hash space already defined
     * @throws InvalidHashRangeException
     * @throws IOException 
     */
    public static void RejoinNode(String addr, int port, HashSpace hashSpace) throws InvalidHashRangeException, IOException {
        // Add the node to the nodes list and remove from dead nodes list
    	ServerNode node = null;
    	InetAddress address = InetAddress.getByName(addr);

		for (Iterator<ServerNode> iter = DeadNodes.iterator(); iter.hasNext();) {
			ServerNode n = iter.next();

			if (n.getAddress().equals(address) && n.getPort() == port) {
				iter.remove();
				node = n;
				break;
			}
		}

		//Node was never in deadnodes in the first place
		if (node == null) return;

    	ServerNodes.add(node);
    	
    	// Transfer hash space
    	for (ServerNode n : ServerNodes) {
    	    // If the node in the over the current hashspace
    		if (n.hasHashSpace(hashSpace)) {

    		    // Update its hashSpace
    			n.removeHashSpace(hashSpace);
        		
            	// If new node is taking over this node's hashspace, transfer keys
            	if (n.equals(selfNode)) {
            	    TransferKeys(addr, port, hashSpace);
            	    break;
                }
        	}
    	}
    	
    	for (int i = 0; i < ServerNodes.size(); i++) {

    		node = ServerNodes.get(i);
    		System.out.println("[In Rejoin]" + node.getAddress().getHostName() + ":" + node.getPort() + ", Range: " + node.getHashSpaces().get(0).toString());
    		for (HashSpace hs : node.getHashSpaces()) {
                System.out.println(hs.toString());
            }
    	}
    }
    
    private static void TransferKeys(String addr, int port, HashSpace hashSpace) throws UnknownHostException, IOException {
        DatagramSocket socket = new DatagramSocket();

        for (Map.Entry<KVMapKey, KVMapValue> entry : requestProcessor.kvMap.entrySet()) {
            KVMapKey k = entry.getKey();
            KVMapValue v = entry.getValue();
            int hash = k.getHash();

            if (hashSpace.inSpace(hash)) {
                KeyValueRequest.KVRequest request = KeyValueRequest.KVRequest.newBuilder()
                        .setCommand(1)
                        .setKey(ByteString.copyFrom(k.getKey()))
                        .setValue(ByteString.copyFrom(v.getValue()))
                        .setVersion(v.getVersion())
                        .build();

                ByteString uuid = Worker.GetUUID(socket);
                Message.Msg msg = Client.PackMessage(request, uuid.toByteArray(), 1);
                DatagramPacket packet = new DatagramPacket(msg.toByteArray(), msg.getSerializedSize(), InetAddress.getByName(addr), port);

                Worker.SendAndReceive(socket, packet, uuid, 3);
            }
        }

        requestProcessor.kvMap.keySet().removeIf(k ->
        	hashSpace.inSpace(k.getHash())
        );

        socket.close();
    }

    public static void LaunchWorkerWithPriority(DatagramPacket packet, int priority) {
        threadPool.execute(new Worker(packet, priority));
    }

    public void StartServing() throws SocketException  {
        System.out.println("Listening on: " + this.listeningSocket.getLocalPort());
        System.out.println("CPUs: " + this.availableCores);

        // Launch the epidemic service to update nodes state across the ring
        EpidemicServer.start();

        // Launch the FailureCheck and RejoinCheck threads
        FailureCheck.start();

        // Send epidemic to other nodes
        System.out.println("Alerting other nodes");
        AlertOtherNodes();

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
    }
}


