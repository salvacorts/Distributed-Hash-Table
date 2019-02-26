package com.g9A.CPEN431.A6.server;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

import com.g9A.CPEN431.A6.client.exceptions.DifferentChecksumException;
import com.g9A.CPEN431.A6.client.exceptions.DifferentUUIDException;
import com.g9A.CPEN431.A6.server.cache.CacheManager;
import com.g9A.CPEN431.A6.server.exceptions.*;
import com.g9A.CPEN431.A6.server.kvMap.RequestProcessor;
import com.g9A.CPEN431.A6.server.network.Epidemic;
import com.g9A.CPEN431.A6.utils.ByteOrder;
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
    private static DatagramSocket staticSocket = null;

    private DatagramSocket socket;
    private DatagramPacket packet;
    private CacheManager cache;
    private RequestProcessor requestProcessor;

    /* UUID (16B):
        IP (4B) + Port (2B) + Random num (2B) + timestamp (8B)
     */
    private static ByteString GetUUID(DatagramSocket socket) {
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

    public static Message.Msg UnpackMessage(DatagramPacket packet) throws com.google.protobuf.InvalidProtocolBufferException {
		return Message.Msg.newBuilder()
			.mergeFrom(packet.getData(), 0, packet.getLength())
			.build();
    }

    private static KeyValueRequest.KVRequest UnpackKVRequest(Message.Msg msg) throws com.google.protobuf.InvalidProtocolBufferException {
        //return KeyValueRequest.KVRequest.parseFrom(msg.getPayload());

        return KeyValueRequest.KVRequest.newBuilder().mergeFrom(msg.getPayload()).build();
    }
    
    private static KeyValueResponse.KVResponse UnpackResponse(Message.Msg msg) throws com.google.protobuf.InvalidProtocolBufferException,
                                                                                      com.g9A.CPEN431.A6.client.exceptions.DifferentChecksumException {
        if (!CorrectChecksum(msg)) throw new DifferentChecksumException();

        return KeyValueResponse.KVResponse.parseFrom(msg.getPayload());
    }

    /**
     * Pack a message calculating checksum
     * @param response payload
     * @param uuid message ID
     * @return the message to be sent with checksum
     */
    private static Message.Msg PackMessage(KeyValueResponse.KVResponse response, ByteString uuid) {
        CRC32 crc32 = new CRC32();
        crc32.update(ByteOrder.concatArray(uuid.toByteArray(), response.toByteArray()));

        long checksum = crc32.getValue();

        return Message.Msg.newBuilder()
                .setMessageID(uuid)
                .setPayload(response.toByteString())
                .setCheckSum(checksum)
                .build();
    }
    
    /**
     * Pack a message calculating checksum
     * @param request original client request
     * @param uuid new message uuid
     * @param address client address
     * @param port client port
     * @return the message to be sent with checksum
     */
    /*private static Message.Msg PackMessageForward(Message.Msg request, ByteString uuid, InetAddress address, int port) {
        CRC32 crc32 = new CRC32();
        byte[] concat = ByteOrder.concatArray(uuid.toByteArray(), request.getPayload().toByteArray());
        crc32.update(concat);

        long checksum = crc32.getValue();
        ByteString addressBytes = ByteString.copyFrom(address.getAddress());

        return Message.Msg.newBuilder()
                .setMessageID(uuid)
                .setPayload(request.getPayload())
                .setCheckSum(checksum)
                .setClient(
                        Message.ClientInfo.newBuilder()
                            .setAddress(addressBytes)
                            .setPort(port)
                            .setMessageID(request.getMessageID())
                            .build()
                )
                .build();
    }/*

    /**
     * Send packet to another node and wait for confirmation
     * @param packet
     * @param maxRetires
     * @return response from the other node, usually a error code 0 (success)
     * @throws IOException when SocketTimeOutException. It means the other node is probably down
     */
    private KVResponse SendAndReceive(DatagramPacket packet, ByteString uuid, int maxRetires) throws IOException {
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
     * @param hash hash of the key
     * @throws IOException
     */
    private KVResponse Reroute(Message.Msg request, int hash) throws IOException{
    	for (ServerNode node : Server.serverNodes) {
    	    if (node.inSpace(hash)) {
                // Serialize message
                DatagramPacket send_packet = new DatagramPacket(request.toByteArray(), request.getSerializedSize(), node.getAddress(), node.getPort());

                try {
                    // Send packet to correct node and obtain an answer
                    return SendAndReceive(send_packet, request.getMessageID(), 3);
                } catch (SocketTimeoutException e) {
                    // The correct node seems to be down, answer to the client
                    // TODO A7: Handle node failure internally (update alive nodes list, and start storing keys if necessary)
                    System.err.println("[!Down?] Could not contact " + node.getAddress().getHostName() + ":" + node.getPort());
                }

                break;
    	    }
        }

    	return KVResponse.newBuilder().setErrCode(1).build();   // Send un-existing key error;
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
    void SendOverloadWaitTime(@NotNull DatagramPacket packet, long time) throws java.io.IOException {
        ByteString uuid = UnpackMessage(packet).getMessageID();

        KeyValueResponse.KVResponse response = KeyValueResponse.KVResponse.newBuilder()
                    .setErrCode(3)
                    .setOverloadWaitTime((int) time)
                .build();

        Message.Msg msg = PackMessage(response, uuid);

        Send(msg, packet.getAddress(), packet.getPort());
    }

    private void Send(Message.Msg msg, InetAddress address, int port) throws IOException {
        if (socket == null) socket = new DatagramSocket();

        // Serialize message
        DatagramPacket send_packet = new DatagramPacket(msg.toByteArray(), msg.getSerializedSize(), address, port);

        // Send packet
        socket.send(send_packet);
    }

    Worker(DatagramPacket packet, DatagramSocket socket) {
        this.packet = packet;
        this.socket = socket;

        this.cache = CacheManager.getInstance();
        this.requestProcessor = RequestProcessor.getInstance();
    }

    public void run() {
        long startTime = System.currentTimeMillis();

        try {
            KeyValueResponse.KVResponse response;
            Message.Msg rec_msg = null;

            // Unpack message from the packet
        	try {
        		rec_msg = UnpackMessage(packet);
        	} catch (com.google.protobuf.InvalidProtocolBufferException e) {
        		e.printStackTrace();
                Server.socketPool.returnObject(socket);
                return;
        	}

            // If packet has been rerouted by other node, update destination info
            /*if (rec_msg.hasClient()) {
                //System.out.println("Received packet rerouted");

                // Check if the confirmation for the other node is cached
                response = this.cache.Get(rec_msg.getMessageID());

                if (response == null) {
                    response = KVResponse.newBuilder().setErrCode(0).build();

                    this.cache.Put(rec_msg.getMessageID(), response);
                }

                // Send confirmation to the other node
                Message.Msg response_msg = PackMessage(response, rec_msg.getMessageID());
                Send(response_msg, packet.getAddress(), packet.getPort());

                // Update address to the original client in order to answer him and not to the other node
                byte[] addr = rec_msg.getClient().getAddress().toByteArray();
                int port = rec_msg.getClient().getPort();

                packet.setAddress(InetAddress.getByAddress(addr));
                packet.setPort(port);
            }*/

            // Get the uuid for this message. It may be a forwarded message
            //ByteString uuid = (rec_msg.hasClient()) ? rec_msg.getClient().getMessageID() : rec_msg.getMessageID();
            ByteString uuid = rec_msg.getMessageID();

            try {
                // Check if checksum is correct
                if (!CorrectChecksum(rec_msg)) {
                    Server.socketPool.returnObject(socket);
                	return;
                }

                // If uuid is in processing map, return and let the other thread to finish this work
                if (processing_messages.contains(uuid)) {
                    Server.socketPool.returnObject(socket);
                	return;
                }

                // Add this message to the messages being processed set
                processing_messages.add(uuid);

                // Check if request is cached. If it is not process the request
                if ((response = this.cache.Get(uuid))  == null) {
                	if (!rec_msg.hasType() || rec_msg.getType() == 1) {
                		KeyValueRequest.KVRequest request = UnpackKVRequest(rec_msg);
                        response = requestProcessor.ProcessRequest(request, uuid);
                        this.cache.Put(uuid, response);
                	} else {
                        Server.socketPool.returnObject(socket);
                		return;
                	}
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
                response = Reroute(rec_msg, e.getHash());

                // Otherwise, send the error to the client catching the response
                this.cache.Put(rec_msg.getMessageID(), response);

            } catch (Exception e) {
                e.printStackTrace();
                response = KeyValueResponse.KVResponse.newBuilder()
                        .setErrCode(4)
                        .build();
            }

            // Pack the message with the response
            Message.Msg response_msg = PackMessage(response, uuid);

            // Send message to destination
            Send(response_msg, packet.getAddress(), packet.getPort());

            // Remove this uuid from the being processed set
            processing_messages.remove(uuid);

            // Update process time in server
            long endTime = System.currentTimeMillis();
            Server.UpdateProcessTime(endTime - startTime);

            // Return socket to it's pool
            Server.socketPool.returnObject(socket);
        } catch (Exception e) {
            e.printStackTrace();
            Server.socketPool.returnObject(socket);
        }

    }
}
