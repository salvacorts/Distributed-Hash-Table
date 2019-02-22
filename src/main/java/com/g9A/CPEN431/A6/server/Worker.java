package com.g9A.CPEN431.A6.server;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import ca.NetSysLab.ProtocolBuffers.Message;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

import com.g9A.CPEN431.A6.server.exceptions.*;
import com.g9A.CPEN431.A6.utils.ByteOrder;
import com.google.protobuf.ByteString;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;

class Worker implements Runnable {
    // Set to manage processes being processed at a time
    private static Set<ByteString> processing_messages = Collections.synchronizedSet(new HashSet<ByteString>());
    private static DatagramSocket staticSocket = null;

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
     * @param payload payload
     * @param uuid message ID
     * @return the message to be sent with checksum
     */
    private static Message.Msg PackMessage(ByteString payload, ByteString uuid, InetAddress address, int port) {
        CRC32 crc32 = new CRC32();
        byte[] concat = ByteOrder.concatArray(uuid.toByteArray(), payload.toByteArray());
        crc32.update(concat);

        long checksum = crc32.getValue();
        ByteString addressBytes = ByteString.copyFrom(address.getAddress());

        return Message.Msg.newBuilder()
                .setMessageID(uuid)
                .setPayload(payload)
                .setCheckSum(checksum)
                .setClient(
                        Message.ClientInfo.newBuilder()
                            .setAddress(addressBytes)
                            .setPort(port)
                            .build()
                )
                .build();
    }
    
    /**
     * Routes a request to the correct node
     * @param request the request to reroute
     * @param hash hash of the key
     * @throws IOException
     */
    private void Reroute(Message.Msg request, int hash) throws IOException{
    	for (ServerNode node : Server.serverNodes) {
    	    if (node.inSpace(hash)) {
                Message.Msg send_msg = PackMessage(request.getPayload(), request.getMessageID(), packet.getAddress(), packet.getPort());

                // Serialize message
                byte[] sendData = send_msg.toByteArray();
                DatagramPacket send_packet = new DatagramPacket(sendData, sendData.length, node.getAddress(), node.getPort());

                // Use socket assigned from worker thread (avoid mem leak)
                socket.send(send_packet);
            }
        }
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

        // Serialize message
        byte[] sendData = msg.toByteArray();
        DatagramPacket send_packet = new DatagramPacket(sendData, sendData.length, packet.getAddress(), packet.getPort());

        // Send the message through socket assigned by factory (avoid mem leak w/ finalizer)
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

            // If packet has been rerouted by other node, update destination info
            if (rec_msg.hasClient()) {
                //System.out.println("Received packet rerouted");

                byte[] addr = rec_msg.getClient().getAddress().toByteArray();
                int port = rec_msg.getClient().getPort();

                packet.setAddress(InetAddress.getByAddress(addr));
                packet.setPort(port);
            }

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
            } catch (WrongNodeException e) {
                //System.out.println("Rerouting to correct node");
                Reroute(rec_msg, e.getHash());
                return; // The other node will answer
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
