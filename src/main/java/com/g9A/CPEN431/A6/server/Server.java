package com.g9A.CPEN431.A6.server;

// TODO: CATCH com.google.protobuf.InvalidProtocolBufferException: While parsing a protocol message, the input ended unexpectedly in the middle of a field.  This could mean either than the input has been truncated or that an embedded message misreported its own length. PROPERLY
// TODO: Calc if PUT will be successful based on heap size and used size.

import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.g9A.CPEN431.A6.server.network.EpidemicServer;
import com.g9A.CPEN431.A6.server.pools.SocketFactory;

import com.g9A.CPEN431.A6.server.pools.SocketPool;

public class Server {
    static boolean KEEP_RECEIVING = true;
    static long avgProcessTime = 0;

    private DatagramSocket listeningSocket;        // Server UDP socket to send and receive packets
    private int availableCores;

    public static ExecutorService threadPool;
    public static SocketPool socketPool = new SocketPool(new SocketFactory());
    public static ServerNode selfNode;
    public static List<ServerNode> serverNodes;
    public static EpidemicServer epiSrv;

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

        serverNodes = otherNodes;

        InetAddress local = InetAddress.getLocalHost();

        for (ServerNode node : serverNodes) {
            if (port == node.getPort() && (local.equals(node.getAddress())
                                            || node.getAddress().getHostAddress().equals("127.0.0.1"))) {
                selfNode = node;
                break;
            }
        }

        if (selfNode == null) {
        	throw new IllegalArgumentException("Current server not present in nodes-list");
        }

        // Launch the epidemic service to update nodes state across the ring
        epiSrv = new EpidemicServer(epiPort);
        epiSrv.start();
    }

    public static void removeNode(String addr, int port) {
        // Remove the node from the nodes list
    	for (Iterator<ServerNode> iter = serverNodes.listIterator(); iter.hasNext(); ) {
    		ServerNode node = iter.next();

    	    if (addr.equals(node.getAddress().getHostAddress()) && node.getPort() == port) {
    	    	
            	// Re-balance hash space
            	if(!iter.hasNext()) {
            		ServerNode lastNode = serverNodes.get(serverNodes.size()-2);
            		lastNode.setHashRange(lastNode.getHashStart(), node.getHashEnd());
            	}
            	else {
        	        ServerNode nextNode = iter.next();
            		nextNode.setHashRange(node.getHashStart(), nextNode.getHashEnd());
            	}
    	        serverNodes.remove(node);
    	        

    	    	/*for (int i = 0; i < serverNodes.size(); i++) {

    	    		//serverNodes.get(i).setHashRange(start, end);
    	    		node = serverNodes.get(i);
    	    		System.out.println(node.getAddress().getHostName() + ":" + node.getPort() + ", Range: " + node.getHashStart() + "-" + node.getHashEnd());
    	    	}*/
    	        
    	        return;
    	    }
    	}
		System.err.println("Attempted to remove nonexistent node");
    }

    public static void LaunchWorkerWithPriority(DatagramPacket packet, int priority) {
        threadPool.execute(new Worker(packet, priority));
    }

    public void StartServing() {
        System.out.println("Listening on: " + this.listeningSocket.getLocalPort());
        System.out.println("CPUs: " + this.availableCores);

        while (KEEP_RECEIVING) {
            byte[] receiveData = new byte[65507];
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

        System.out.println("Server stopping");
        this.listeningSocket.close();
        threadPool.shutdown();
        socketPool.close();
        epiSrv.stop();
    }
}


