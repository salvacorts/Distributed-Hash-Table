package com.g9A.CPEN431.A6.server;

// TODO: CATCH com.google.protobuf.InvalidProtocolBufferException: While parsing a protocol message, the input ended unexpectedly in the middle of a field.  This could mean either than the input has been truncated or that an embedded message misreported its own length. PROPERLY
// TODO: Calc if PUT will be successful based on heap size and used size.

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.prometheus.client.Gauge;

public class Server {
    static boolean KEEP_RECEIVING = true;

    private static long avgProcessTime = 0;
    private DatagramSocket listeningSocket;        // Server UDP socket to send and receive packets
    private int availableCores;
    private ExecutorService threadPool;
    private WorkerThreadFactory threadFactory;

    public static ServerNode selfNode;
    public static ArrayList<ServerNode> serverNodes;

    static void UpdateProcessTime(long time) {
        avgProcessTime = (avgProcessTime + time) / 2;
    }

    public Server(int port, ArrayList<ServerNode> otherNodes) throws java.net.SocketException, UnknownHostException {
        this.listeningSocket = new DatagramSocket(port);
        this.availableCores = Runtime.getRuntime().availableProcessors();

        int coresPool = (this.availableCores > 1) ? this.availableCores - 1 : 1;

        this.threadFactory = new WorkerThreadFactory(coresPool);
        this.threadPool = Executors.newFixedThreadPool(coresPool, threadFactory);
        this.serverNodes = otherNodes;

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
    }

    public void StartServing() {
        byte[] receiveData = new byte[65507];

        System.out.println("Listening on: " + this.listeningSocket.getLocalPort());
        System.out.println("CPUs: " + this.availableCores);

        while (KEEP_RECEIVING) {
            DatagramPacket rec_packet = new DatagramPacket(receiveData, receiveData.length);

            try {
                // Receive a packet
                listeningSocket.receive(rec_packet);

                // Launch a new worker on the pool
                this.threadPool.execute(new Worker(rec_packet, threadFactory.GetSocketToUse()));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        this.listeningSocket.close();
        this.threadPool.shutdown();
        this.threadFactory.dispose();
    }
}


