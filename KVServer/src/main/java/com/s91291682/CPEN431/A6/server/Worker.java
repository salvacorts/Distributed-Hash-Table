package com.s91291682.CPEN431.A6.server;

import com.s91291682.CPEN431.A6.server.exceptions.*;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

import com.google.protobuf.ByteString;
import com.s91291682.CPEN431.A6.server.exceptions.ShutdownCommandException;
import com.s91291682.CPEN431.A6.utils.ByteOrder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;

class Worker implements Runnable {
    // Set to manage processes being processed at a time
    private static Set<ByteString> processing_messages = Collections.synchronizedSet(new HashSet<ByteString>());

    private DatagramSocket socket;
    private DatagramPacket packet;
    private CacheManager cache;
    private RequestProcessor requestProcessor;

    private static Message.Msg UnpackMessage(DatagramPacket packet) throws com.google.protobuf.InvalidProtocolBufferException {
        byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());

        return Message.Msg.parseFrom(data);
    }

    private static KeyValueRequest.KVRequest UnpackRequest(Message.Msg msg) throws com.google.protobuf.InvalidProtocolBufferException {
        return KeyValueRequest.KVRequest.parseFrom(msg.getPayload());
    }
    
    private static KeyValueResponse.KVResponse UnpackResponse(Message.Msg msg) throws com.google.protobuf.InvalidProtocolBufferException {
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
        byte[] concat = ByteOrder.concatArray(uuid.toByteArray(), response.toByteArray());
        crc32.update(concat);

        long checksum = crc32.getValue();

        return Message.Msg.newBuilder()
                .setMessageID(uuid)
                .setPayload(response.toByteString())
                .setCheckSum(checksum)
                .build();
    }
    
    /**
     * Pack a message calculating checksum
     * @param response payload
     * @param uuid message ID
     * @return the message to be sent with checksum
     */
    private static Message.Msg PackMessage(KeyValueRequest.KVRequest request, ByteString uuid) {
        CRC32 crc32 = new CRC32();
        byte[] concat = ByteOrder.concatArray(uuid.toByteArray(), request.toByteArray());
        crc32.update(concat);

        long checksum = crc32.getValue();

        return Message.Msg.newBuilder()
                .setMessageID(uuid)
                .setPayload(request.toByteString())
                .setCheckSum(checksum)
                .build();
    }
    
    /**
     * Routes a request to the correct node
     * @param request the request to reroute
     * @return The result of the routed request/error response if no correct node can be found
     * @throws IOException 
     */
    static KVResponse Reroute(KeyValueRequest.KVRequest request, ByteString messageId) throws IOException{
    	ByteString key = request.getKey();
    	int hash = key.hashCode()%256;
    	
    	for(int i = 0; i < Server.serverNodes.length; i++) {
    		if(Server.serverNodes[i].inSpace(hash)) {
                Message.Msg send_msg = PackMessage(request, messageId);

                // Serialize message
                byte[] sendData = send_msg.toByteArray();
                byte[] receiveData = new byte[65507];
                DatagramPacket rec_packet = new DatagramPacket(receiveData, receiveData.length);
    			DatagramPacket send_packet = new DatagramPacket(sendData, sendData.length, 
    					Server.serverNodes[i].getAddress(), Server.serverNodes[i].getPort());

    	        DatagramSocket socket = new DatagramSocket();
    	        socket.send(send_packet);
    	        
    	        socket.receive(rec_packet);
                Message.Msg rec_msg = UnpackMessage(rec_packet);
                KVResponse res = UnpackResponse(rec_msg);
                socket.close();
                return res;
    		}
    	}
    	return KVResponse.newBuilder()
    			.setErrCode(4)
    			.build();
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
    static void SendOverloadWaitTime(@NotNull DatagramPacket packet, long time) throws java.io.IOException {
        ByteString uuid = UnpackMessage(packet).getMessageID();

        KeyValueResponse.KVResponse response = KeyValueResponse.KVResponse.newBuilder()
                    .setErrCode(3)
                    .setOverloadWaitTime((int) time)
                .build();

        Message.Msg msg = PackMessage(response, uuid);

        // Serialize message
        byte[] sendData = msg.toByteArray();
        DatagramPacket send_packet = new DatagramPacket(sendData, sendData.length, packet.getAddress(), packet.getPort());

        // Send the message
        DatagramSocket socket = new DatagramSocket();
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
            // Unpack message from the packet
            Message.Msg rec_msg = UnpackMessage(packet);
            KeyValueResponse.KVResponse response;

            try {
                // Check if checksum is correct
                if (!CorrectChecksum(rec_msg)) return;

                // If uuid is in processing map, return and let the other thread to finish this work
                if (processing_messages.contains(rec_msg.getMessageID())) return;

                // Add this message to the messages being processed set
                processing_messages.add(rec_msg.getMessageID());

                // Check if request is cached. If it is not process the request
                KeyValueResponse.KVResponse cachedResponse = this.cache.Get(rec_msg.getMessageID());

                if (cachedResponse != null) {
                    response = cachedResponse;
                } else {
                    KeyValueRequest.KVRequest request = UnpackRequest(rec_msg);
                    response = requestProcessor.ProcessRequest(request, rec_msg.getMessageID());
                    this.cache.Put(rec_msg.getMessageID(), response);
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
                        .setErrCode(2)
                        .build();
            } catch (Exception e) {
                e.printStackTrace();
                response = KeyValueResponse.KVResponse.newBuilder()
                        .setErrCode(4)
                        .build();
            }

            // Process request and get a response
            Message.Msg response_msg = PackMessage(response, rec_msg.getMessageID());

            // Serialize message
            byte[] sendData = response_msg.toByteArray();
            DatagramPacket send_packet = new DatagramPacket(sendData, sendData.length, packet.getAddress(), packet.getPort());

            // Remove this uuid from the being processed set
            processing_messages.remove(rec_msg.getMessageID());

            // Send packet
            if (socket == null) socket = new DatagramSocket();
            socket.send(send_packet);

            // Update process time in server
            long endTime = System.currentTimeMillis();
            Server.UpdateProcessTime(endTime - startTime);
        } catch (Exception e) {
            //e.printStackTrace();
        }
    }
}
