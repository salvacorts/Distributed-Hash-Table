package com.g9A.CPEN431.A11.server;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueRequest.KVRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

import com.g9A.CPEN431.A11.client.exceptions.DifferentChecksumException;
import com.g9A.CPEN431.A11.client.exceptions.DifferentUUIDException;
import com.g9A.CPEN431.A11.server.cache.CacheManager;
import com.g9A.CPEN431.A11.server.exceptions.*;
import com.g9A.CPEN431.A11.server.kvMap.RequestProcessor;
import com.g9A.CPEN431.A11.utils.ByteOrder;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.zip.CRC32;

public class Worker implements Runnable {
    // Set to manage processes being processed at a time
    private static Set<ByteString> processing_messages = Collections.synchronizedSet(new HashSet<ByteString>());

    private DatagramSocket socket;
    private DatagramPacket packet;
    private CacheManager cache;
    private RequestProcessor requestProcessor;
    private int priority;

    /* UUID (16B):
        IP (4B) + Port (2B) + Random num (2B) + timestamp (8B)
     */
    public static ByteString GetUUID(DatagramSocket socket) {
        Random randomGen = new Random();
        byte[] buffUuid = new byte[16];

        byte[] ip = socket.getLocalAddress().getAddress();
        short port = (short) socket.getLocalPort();
        short rnd = (short) randomGen.nextInt(Short.MAX_VALUE + 1);
        long timestamp = System.nanoTime();

        System.arraycopy(ip, 0, buffUuid, 0, ip.length);
        ByteOrder.short2leb(port, buffUuid, 4);
        ByteOrder.short2leb(rnd, buffUuid, 6);
        ByteOrder.long2leb(timestamp, buffUuid, 8);

        return ByteString.copyFrom(buffUuid);
    }

    public static Message.Msg UnpackMessage(DatagramPacket packet) throws com.google.protobuf.InvalidProtocolBufferException,
                                                                          DifferentChecksumException {
		Message.Msg unpacked = Message.Msg.newBuilder()
			.mergeFrom(packet.getData(), 0, packet.getLength())
			.build();

		if (!CorrectChecksum(unpacked)) throw new DifferentChecksumException();

		return unpacked;
    }

    public static KeyValueRequest.KVRequest UnpackKVRequest(Message.Msg msg) throws com.google.protobuf.InvalidProtocolBufferException {
        //return KeyValueRequest.KVRequest.parseFrom(msg.getPayload());

        return KeyValueRequest.KVRequest.newBuilder().mergeFrom(msg.getPayload()).build();
    }
    
    public static InternalRequest.KVTransfer UnpackKVTransfer(Message.Msg msg) throws com.google.protobuf.InvalidProtocolBufferException {
        //return KeyValueRequest.KVRequest.parseFrom(msg.getPayload());

        return InternalRequest.KVTransfer.newBuilder().mergeFrom(msg.getPayload()).build();
    }
    
    public static KeyValueResponse.KVResponse UnpackResponse(Message.Msg msg) throws com.google.protobuf.InvalidProtocolBufferException,
                                                                                     DifferentChecksumException {
        if (!CorrectChecksum(msg)) throw new DifferentChecksumException();

        return KeyValueResponse.KVResponse.parseFrom(msg.getPayload());
    }

    /**
     * Pack a message calculating checksum
     * @param response payload
     * @param uuid message ID
     * @return the message to be sent with checksum
     */
    public static Message.Msg PackMessage(KeyValueResponse.KVResponse response, ByteString uuid) {
        CRC32 crc32 = new CRC32();
        crc32.update(ByteOrder.concatArray(uuid.toByteArray(), response.toByteArray()));

        long checksum = crc32.getValue();

        return Message.Msg.newBuilder()
                .setMessageID(uuid)
                .setPayload(response.toByteString())
                .setCheckSum(checksum)
                .build();
    }
    
    public static Message.Msg PackMessage(InternalRequest.KVTransfer request, ByteString uuid) {
        CRC32 crc32 = new CRC32();
        crc32.update(ByteOrder.concatArray(uuid.toByteArray(), request.toByteArray()));

        long checksum = crc32.getValue();

        return Message.Msg.newBuilder()
                .setMessageID(uuid)
                .setPayload(request.toByteString())
                .setCheckSum(checksum)
                .build();
    }

    /**
     * Send packet to another node and wait for confirmation
     * @param packet
     * @param maxRetires
     * @return response from the other node, usually a error code 0 (success)
     * @throws IOException when SocketTimeOutException. It means the other node is probably down
     */
    public static KVResponse SendAndReceive(DatagramSocket socket, DatagramPacket packet, ByteString uuid, int maxRetires) throws IOException {
        byte[] buffRecv = new byte[65507];  // Max UPD packet size
        int timeout = 1000;

        for (int i = 0; i <= maxRetires; i++) {
            socket.send(packet);

            DatagramPacket recv_packet = new DatagramPacket(buffRecv, buffRecv.length);
            socket.setSoTimeout(timeout);

            try {
                socket.receive(recv_packet);

                Message.Msg rec_msg = UnpackMessage(recv_packet);

                if (!rec_msg.getMessageID().equals(uuid)) throw new DifferentUUIDException();

                return UnpackResponse(rec_msg);

            } catch (SocketTimeoutException e) {
                System.err.println("Timeout connecting with " + packet.getAddress().getHostName() + ":" + packet.getPort() + "\t(" + timeout + "ms)");
                timeout *= 2;
            } catch (Exception e) { // Can be either Different UUID or incorrect Checksum
                i--;
            }
        }

        throw new SocketTimeoutException();
    }
    
    /**
     * Routes a request to the correct node
     * @param request the request to reroute
     * @param clientAddr address of the original client
     * @param clientPort port of the original client
     * @param correctNode the node to reroute to
     * @throws IOException
     */
    private void Reroute(Message.Msg request, InetAddress clientAddr, int clientPort, ServerNode correctNode) throws IOException{
    	
    	ByteString addr = ByteString.copyFrom(clientAddr.getAddress());

    	Message.ClientInfo client = Message.ClientInfo.newBuilder()
    			.setAddress(addr)
    			.setPort(clientPort)
    			.build();

    	Message.Msg newRequest = Message.Msg.newBuilder()
    			.setMessageID(request.getMessageID())
    			.setPayload(request.getPayload())
    			.setCheckSum(request.getCheckSum())
    			.setClient(client)
    			.build();
    	

		Send(socket, newRequest, correctNode.getAddress(), correctNode.getPort());
    }

    private static boolean CorrectChecksum(Message.Msg msg) {
        CRC32 crc32 = new CRC32();
        byte[] concat = ByteOrder.concatArray(msg.getMessageID().toByteArray(), msg.getPayload().toByteArray());
        crc32.update(concat);

        long rec_checksum = msg.getCheckSum();
        long rec_sum = crc32.getValue();

        return rec_checksum == rec_sum;
    }

    /**
     * Send a waitForTime message through this socket
     * @param packet packet received
     * @param time waitForTime in milliseconds
     */
    void SendOverloadWaitTime(@NotNull DatagramPacket packet, long time) throws java.io.IOException, DifferentChecksumException {
        ByteString uuid = UnpackMessage(packet).getMessageID();

        KeyValueResponse.KVResponse response = KeyValueResponse.KVResponse.newBuilder()
                    .setErrCode(3)
                    .setOverloadWaitTime((int) time)
                .build();

        Message.Msg msg = PackMessage(response, uuid);

        Send(socket, msg, packet.getAddress(), packet.getPort());
    }

    public static void Send(DatagramSocket socket, Message.Msg msg, InetAddress address, int port) throws IOException {
        boolean newSocket = socket == null;
    	if (newSocket) socket = new DatagramSocket();

        // Serialize message
        DatagramPacket send_packet = new DatagramPacket(msg.toByteArray(), msg.getSerializedSize(), address, port);

        // Send packet
        socket.send(send_packet);
        if(newSocket) socket.close();
    }

    public Worker(DatagramPacket packet) {
        this(packet, Thread.NORM_PRIORITY);
    }

    public Worker(DatagramPacket packet, int priority) {
        try {
            this.priority = priority;
            this.packet = packet;
            this.socket = Server.socketPool.borrowObject();
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.cache = CacheManager.getInstance();
        this.requestProcessor = RequestProcessor.getInstance();
    }
    
    private void ReplicateRequest(Message.Msg msg, KeyValueRequest.KVRequest request) throws IOException {
    	int reps;
    	if(request.hasReps()) {
    		reps = request.getReps() - 1;
        	if(reps < 1) {
        		return;
        	}
    	}
    	else {
    		reps = 2;
    	}
    	int command = request.getCommand();
    	
    	KVRequest newRequest = request.toBuilder()
    			.setReps(reps)
    			.build();
    	
    	CRC32 crc32 = new CRC32();
    	byte[] concat = ByteOrder.concatArray(msg.getMessageID().toByteArray(), newRequest.toByteArray());
        crc32.update(concat);
        long checksum = crc32.getValue();
    	
    	Message.Msg newMessage = msg.toBuilder()
    			.setPayload(newRequest.toByteString())
    			.setCheckSum(checksum)
    			.build();
    	
    	switch(command) {
    	case 3:
    	case 1:
    		int hash = Server.selfNode.getHashValues()[0];
    		int original = hash;
    		hash = (hash+1)%256;
            byte[] buffSend = newMessage.toByteArray();

			ServerNode server;
			while(hash != original){
    			server = Server.HashCircle.get(hash);
    			if(server != null && !server.equals(Server.selfNode)) {
    	    		DatagramSocket socket = new DatagramSocket();
    	            DatagramPacket packet = new DatagramPacket(buffSend, buffSend.length, server.getAddress(), server.getPort());
    	            socket.send(packet);
    	            socket.close();
    	            return;
    			}
    			hash = (hash+1)%256;
    		}
    		break;
    	default:
    		return;
    	}
    }

    public void run() {
        long startTime = System.currentTimeMillis();
        Thread.currentThread().setPriority(this.priority);

        try {
            KeyValueResponse.KVResponse response;
            Message.Msg rec_msg;

            // Unpack message from the packet
            rec_msg = UnpackMessage(packet);

            // If packet has been rerouted by other node, update destination info
            if (rec_msg.hasClient()) {
                // Update address to the original client in order to answer him and not to the other node
                byte[] addr = rec_msg.getClient().getAddress().toByteArray();
                int port = rec_msg.getClient().getPort();

                packet.setAddress(InetAddress.getByAddress(addr));
                packet.setPort(port);
            }

            // Get the uuid for this message. It may be a forwarded message
            ByteString uuid = rec_msg.getMessageID();

            try {
                // If uuid is in processing map, return and let the other thread to finish this work
                if (processing_messages.contains(uuid)) return;

                // Add this message to the messages being processed set
                processing_messages.add(uuid);

                // Check if request is cached. If it is not process the request
                if ((response = this.cache.Get(uuid))  == null) {
                    KeyValueRequest.KVRequest request = UnpackKVRequest(rec_msg);
                    response = requestProcessor.ProcessRequest(request, uuid);
                    this.cache.Put(uuid, response);
                    ReplicateRequest(rec_msg, request);
                }

            } catch (ShutdownCommandException e) {
                Server.KEEP_RECEIVING = false;
                response = KeyValueResponse.KVResponse.newBuilder()
                        .setErrCode(0)
                        .build();
            } catch (OutOfMemoryError e) {
                e.printStackTrace();
                System.gc();    // Run garbage collector
                response = KeyValueResponse.KVResponse.newBuilder()
                        .setErrCode(3)
                        .setOverloadWaitTime((int) Server.avgProcessTime)
                        .build();
            } catch (WrongNodeException e) {
                Reroute(rec_msg, packet.getAddress(), packet.getPort(), e.trueNode);
                processing_messages.remove(uuid);
                Server.socketPool.returnObject(socket);
                return;
            } catch (Exception e) {
                e.printStackTrace();
                response = KeyValueResponse.KVResponse.newBuilder()
                        .setErrCode(4)
                        .build();
            }

            // Pack the message with the response
            Message.Msg response_msg = PackMessage(response, uuid);

            // Send message to destination
            Send(socket, response_msg, packet.getAddress(), packet.getPort());

            // Remove this uuid from the being processed set
            processing_messages.remove(uuid);

            // Update process time in server
            long endTime = System.currentTimeMillis();
            Server.UpdateProcessTime(endTime - startTime);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Server.socketPool.returnObject(socket);
        }
    }
}
