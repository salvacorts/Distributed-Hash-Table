package com.g9A.CPEN431.A6.client;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import ca.NetSysLab.ProtocolBuffers.Message;

import com.g9A.CPEN431.A6.client.exceptions.DifferentChecksumException;
import com.g9A.CPEN431.A6.client.exceptions.DifferentUUIDException;
import com.g9A.CPEN431.A6.client.exceptions.UnsupportedCommandException;
import com.g9A.CPEN431.A6.utils.ByteOrder;
import com.google.protobuf.ByteString;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.CRC32;

public class Client {
    private String svrAddr;
    private int svrPort;
    private int maxRetires;

    /* UUID (16B):
        IP (4B) + Port (2B) + Random num (2B) + timestamp (8B)
     */
    private byte[] GetUUID(DatagramSocket socket) {
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

        return buffUuid;
    }

    private KeyValueRequest.KVRequest PackRequest(int reqID, String key, String value, int version) throws UnsupportedCommandException {
        KeyValueRequest.KVRequest.Builder request = KeyValueRequest.KVRequest.newBuilder();

        switch (reqID) {
            case 1:
                // System.out.println("Sending PUT request");
                request.setCommand(1);
                request.setKey(ByteString.copyFromUtf8(key));
                request.setValue(ByteString.copyFromUtf8(value));
                request.setVersion(version);
                break;
            case 2:
                // System.out.println("Sending GET request");
                request.setCommand(2);
                request.setKey(ByteString.copyFromUtf8(key));
                break;
            case 3:
                // System.out.println("Sending REMOVE request");
                request.setCommand(3);
                request.setKey(ByteString.copyFromUtf8(key));
                break;
            case 4:
                // System.out.println("Sending SHUTDOWN request");
                request.setCommand(4);
                break;
            case 5:
                // System.out.println("Sending WIPEOUT request");
                request.setCommand(5);
                break;
            case 6:
                // System.out.println("Sending IsAlive request");
                request.setCommand(6);
                break;
            case 7:
                // System.out.println("Sending GetPID request");
                request.setCommand(7);
                break;
            case 8:
                // System.out.println("Sending GetMembershipCount request");
                request.setCommand(8);
                break;
            default:
                // System.err.println("Command not supported");
                throw new UnsupportedCommandException();
        }

        return request.build();
    }

    private void ProcessResponse(KeyValueResponse.KVResponse response) {
        // System.out.println(response.getErrCode());

        /*
        if (response.hasValue()) // System.out.println(response.getValue().toStringUtf8());
        if (response.hasVersion()) // System.out.println(response.getVersion());
        if (response.hasPid()) // System.out.println(response.getPid());
        if (response.hasMembershipCount()) // System.out.println(response.getMembershipCount());
        if (response.hasOverloadWaitTime()) // System.out.println(response.getOverloadWaitTime());
        /**/
    }

    private Message.Msg PackMessage(KeyValueRequest.KVRequest request, byte[] uuid) {
        CRC32 crc32 = new CRC32();
        Message.Msg.Builder sendMsg = Message.Msg.newBuilder();

        ByteString uuidBS = ByteString.copyFrom(uuid, 0, uuid.length);

        byte[] concat = ByteOrder.concatArray(uuidBS.toByteArray(), request.toByteArray());
        crc32.update(concat);
        long checksum = crc32.getValue();

        sendMsg.setMessageID(uuidBS);
        sendMsg.setPayload(request.toByteString());
        sendMsg.setCheckSum(checksum);

        return sendMsg.build();
    }

    private Message.Msg UnpackMessage(byte[] buffer, byte[] uuid) throws com.google.protobuf.InvalidProtocolBufferException,
                                                                         DifferentChecksumException,
                                                                         DifferentUUIDException {
        Message.Msg msg = Message.Msg.parseFrom(buffer);
        CRC32 crc32 = new CRC32();

        byte[] concat = ByteOrder.concatArray(msg.getMessageID().toByteArray(), msg.getPayload().toByteArray());
        crc32.update(concat);

        byte[] rec_uuid = msg.getMessageID().toByteArray();
        long rec_checksum = msg.getCheckSum();
        long rec_sum = crc32.getValue();

        if (rec_checksum != rec_sum)  throw new DifferentChecksumException();

        if (!ByteOrder.equalByteArray(uuid, rec_uuid)) throw new DifferentUUIDException();

        return msg;
    }

    public Client(String serverAddr, int serverPort, int maxRetires) {
        this.svrAddr = serverAddr;
        this.svrPort = serverPort;
        this.maxRetires = maxRetires;
    }

    public KeyValueResponse.KVResponse DoRequest(int reqID, String key, String value, int version) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(this.svrAddr);
        byte[] buffRecv = new byte[16+16384];
        int timeout = 100;

        byte[] uuid = GetUUID(socket);
        KeyValueRequest.KVRequest request = PackRequest(reqID, key, value, version);
        Message.Msg sendMsg = PackMessage(request, uuid);

        for (int i = 0; i <= this.maxRetires; i++) {
            byte[] buffSend = sendMsg.toByteArray();
            DatagramPacket packet = new DatagramPacket(buffSend, buffSend.length, address, this.svrPort);
            socket.send(packet);

            packet = new DatagramPacket(buffRecv, buffRecv.length);
            socket.setSoTimeout(timeout);

            try {
                socket.receive(packet);
                byte[] data = Arrays.copyOfRange(packet.getData(), 0, packet.getLength());

                Message.Msg rec_msg = UnpackMessage(data, uuid);
                KeyValueResponse.KVResponse response = KeyValueResponse.KVResponse.parseFrom(rec_msg.getPayload());

                ProcessResponse(response);
                return response;

            } catch (SocketTimeoutException e) {
                // System.err.println("Cannot connect with " + this.svrAddr + ":" + this.svrPort + "\t(Waited for " + timeout + "ms)");
                timeout *= 2;
            } catch (DifferentChecksumException e) {
                i--;
            } catch (DifferentUUIDException e) {
                continue;
            }
        }

        if (reqID != 4) throw new SocketTimeoutException();

        return KeyValueResponse.KVResponse.newBuilder().setErrCode(0).build();
    }
}
