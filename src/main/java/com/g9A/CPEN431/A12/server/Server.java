package com.g9A.CPEN431.A12.server;

import java.io.IOException;

// TODO: CATCH com.google.protobuf.InvalidProtocolBufferException: While parsing a protocol message, the input ended unexpectedly in the middle of a field.  This could mean either than the input has been truncated or that an embedded message misreported its own length. PROPERLY
// TODO: Calc if PUT will be successful based on heap size and used size.

import java.lang.management.ManagementFactory;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;

import com.g9A.CPEN431.A12.client.Client;
import com.g9A.CPEN431.A12.server.kvMap.KVMapKey;
import com.g9A.CPEN431.A12.server.kvMap.KVMapValue;
import com.g9A.CPEN431.A12.server.kvMap.RequestProcessor;
import com.g9A.CPEN431.A12.server.metrics.MetricsServer;
import com.g9A.CPEN431.A12.server.network.Epidemic;
import com.g9A.CPEN431.A12.server.network.EpidemicServer;
import com.g9A.CPEN431.A12.server.network.FailureCheck;
import com.g9A.CPEN431.A12.server.network.Ping;
import com.g9A.CPEN431.A12.server.pools.SocketFactory;
import com.g9A.CPEN431.A12.server.pools.SocketPool;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.InternalRequest.EpidemicRequest.EpidemicType;
import sun.misc.Signal;
import sun.misc.SignalHandler;

public class Server {
    static boolean KEEP_RECEIVING = true;
    static long avgProcessTime = 0;

    private DatagramSocket listeningSocket;        // Server UDP socket to send and receive packets
    private int availableCores;

    public static ExecutorService threadPool;
    public static SocketPool socketPool = new SocketPool(new SocketFactory());
    public static ServerNode selfNode;
    public static List<ServerNode> ServerNodes;
	public static List<ServerNode> DeadNodes;
	private static Lock nodesLock = new ReentrantLock();
    public static EpidemicServer epidemicServer;
    public static FailureCheck failureCheck;
    
    public static ConcurrentHashMap<Integer, ServerNode> HashCircle;

    private static MetricsServer metrics = MetricsServer.getInstance();
    public final static Logger LOGGER = Logger.getLogger("ServerLog");
    
    private static RequestProcessor requestProcessor = RequestProcessor.getInstance();
    private static boolean PAUSED = false;

    static void UpdateProcessTime(long time) {
        avgProcessTime = (avgProcessTime + time) / 2;
    }

    @SuppressWarnings("restriction")
	public Server(int port, int epiPort, List<ServerNode> otherNodes) throws Exception {
        this.listeningSocket = new DatagramSocket(port);
        this.availableCores = Runtime.getRuntime().availableProcessors();

        FileHandler fh = new FileHandler("./log.txt");
        LOGGER.addHandler(fh);
        SimpleFormatter formatter = new SimpleFormatter();  
        fh.setFormatter(formatter);  
        
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
        epidemicServer = new EpidemicServer(epiPort);
        failureCheck = new FailureCheck();
        
        HashCircle = new ConcurrentHashMap<Integer, ServerNode>();
        for (ServerNode node : ServerNodes) {
        	for(int i: node.getHashValues()) {
        		HashCircle.put(i, node);
        	}
            if (port == node.getPort() && (local.equals(node.getAddress())
                                            || node.getAddress().getHostAddress().equals("127.0.0.1"))) {
                selfNode = node;
            }
        }

        if (selfNode == null) {
        	System.err.println("Current server not present in nodes-list");
        	System.exit(1);
        }

        // In case that the process is resumed
        try {
        Signal.handle(new Signal("CONT"), new SignalHandler() {
            @Override
            public void handle(Signal signal) {
                try {
                    PAUSED = false;
                    failureCheck.restart();
                    AlertOtherNodes();
                    TestOtherNodes();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        }catch(IllegalArgumentException e) {
        	e.printStackTrace();
        }

        /*// In case that the process is resumed
        Signal.handle(new Signal("STOP"), new SignalHandler() {
            @Override
            public void handle(Signal signal) {
                try {
                    PAUSED = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });*/
    }
    
    private static long lastNodeTest = System.currentTimeMillis()/1000L;
    /**
     * Pings each node to test if it is alive
     */
    public static void TestOtherNodes() {
		long timestamp = System.currentTimeMillis()/1000L;
		if(timestamp - lastNodeTest <= 60) {
			return;
		}
    	
    	Thread[] aliveThreads = new Thread[ServerNodes.size()];
    	Thread[] deadThreads = new Thread[DeadNodes.size()];
    	ServerNode node;
    	for(int i = 0; i < aliveThreads.length; i++) {
    		node = ServerNodes.get(i);
    		aliveThreads[i] = new Thread(new Ping(node));
    		aliveThreads[i].start();
    	}
    	for(int i = 0; i < deadThreads.length; i++) {
    		node = DeadNodes.get(i);
    		deadThreads[i] = new Thread(new Ping(node));
    		deadThreads[i].start();
    	}
    }
    
    /**
     * Notify other nodes this one is online (epidemic protocol)
     * @throws IOException 
     * @throws InvalidHashRangeException 
     */
    public static void AlertOtherNodes() throws IOException {
    	
    	long timestamp = System.currentTimeMillis() / 1000L;
    	
		ByteString id = Epidemic.generateID(selfNode.getAddress(), selfNode.getEpiPort(), EpidemicType.ALIVE);
    	InternalRequest.ServerNode.Builder builder = InternalRequest.ServerNode.newBuilder();
    	builder.setNodeId(selfNode.getId())
    			.setServer(selfNode.getAddress().getHostAddress())
    			.setPort(selfNode.getPort());

    	for(int hash: selfNode.getHashValues()) {
    		builder.addHashes(hash);
    	}
    	
    	InternalRequest.ServerNode serverNode = builder.build();
		
    	
    	InternalRequest.EpidemicRequest epiRequest = InternalRequest.EpidemicRequest.newBuilder()
    			.setServerNode(serverNode)
    			.setEpId(id)
    			.setTimestamp(timestamp)
    			.setType(EpidemicType.ALIVE)
    			.build();
    	
    	metrics.aliveEpidemics.inc();
		Epidemic epi = new Epidemic(epiRequest);
		epidemicServer.add(epi);
    }

    public static void RemoveNode(int id) {

        // Remove the node from the nodes list
    	for (ListIterator<ServerNode> iter = ServerNodes.listIterator(); iter.hasNext(); ) {
    		ServerNode node = iter.next();

      	    if (id == node.getId()) {

                DeadNodes.add(node);
                for(int i: node.getHashValues()) {
                	HashCircle.remove(i);
                }
                iter.remove();
                metrics.deadNodes.inc();
                LOGGER.info("Detected dead node node " + node.getAddress().getHostName() + ":" + node.getPort() + ", ID: " + node.getId());

    	        return;
    	    }
    	}
    }
    
    // Determines if the server has a node currently in DeadNodes, to avoid unnecessary epidemics
    public static boolean HasDeadNode(int id) {
    	for (Iterator<ServerNode> iter = DeadNodes.iterator(); iter.hasNext();) {
			ServerNode n = iter.next();

			if (n.getId() == id) {
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
    public static void RejoinNode(int id, String addr, int port, int[] hashArray) throws IOException {
    	try {
	    	nodesLock.lock();
	    	
	    	// Add the node to the nodes list and remove from dead nodes list
	        ServerNode node = new ServerNode(addr, port, 4321, id, hashArray);
	        if(!HasDeadNode(id)) return;
	        
	        if(ServerNodes.contains(node)) {
	        	return;
	        }
	        
	        LOGGER.info("Rejoining node " + node.getAddress().getHostName() + ":" + node.getPort() + ", ID: " + node.getId());
			
	        ServerNodes.add(node);
	        DeadNodes.remove(node);
	        metrics.deadNodes.dec();
	    	
	    	// Reactivate hash values
	    	for(int i: node.getHashValues()) {
	    		HashCircle.put(i, node);
	    	}
    	}
    	finally {
    		nodesLock.unlock();
    	}
    }
    
    public static void RejoinNode(int id, String addr, int port, List<Integer> hashValues) throws IOException {
        int[] hashArray = new int[hashValues.size()];
    	for(int i = 0; i < hashArray.length; i++) {
        	hashArray[i] = hashValues.get(i);
        }
    	RejoinNode(id, addr, port, hashArray);
    	
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

    public void StartServing() throws IOException  {
        System.out.println("Listening on: " + this.listeningSocket.getLocalPort());
        System.out.println("CPUs: " + this.availableCores);
        System.out.println("PID: " + Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]));

        // Launch the epidemic service to update nodes state across the ring
        epidemicServer.start();

        // Launch the FailureCheck thread
        failureCheck.start();

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
        epidemicServer.stop();
        failureCheck.stop();
    }
}


