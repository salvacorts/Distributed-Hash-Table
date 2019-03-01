package com.g9A.CPEN431.A7.client;

import ca.NetSysLab.ProtocolBuffers.KeyValueResponse.KVResponse;

public class Main {

    public static void main(String[] args) {

        if (args.length != 6) {
            System.err.println("Error: Missing parameters!");
            System.err.println("java -jar client.jar <server address> <server port> <command_id> <key> <value> <version>");
            //return;
        }

        Client client = new Client(args[0], Integer.parseInt(args[1]), 3);
        
        try {
            // System.out.println("Sending ID: " + args[2]);
            KVResponse response = client.DoRequest(Integer.parseInt(args[2]), args[3], args[4], Integer.parseInt(args[5]));
            System.out.println(response.getErrCode());
            System.out.println(response.getValue().toStringUtf8());

        } catch (Exception e) {
            System.err.println(e.toString());
        }

    }
}
