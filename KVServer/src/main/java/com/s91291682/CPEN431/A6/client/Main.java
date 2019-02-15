package com.s91291682.CPEN431.A6.client;

public class Main {

    public static void main(String[] args) {

        if (args.length != 6) {
            // System.err.println("Error: Missing parameters!");
            // System.err.println("java -jar A2.jar <server address> <server port> <command_id> <key> <value> <version>");
            return;
        }

        Client client = new Client(args[0], Integer.parseInt(args[1]), 3);

        try {
            // System.out.println("Sending ID: " + args[2]);

            client.DoRequest(Integer.parseInt(args[2]), args[3], args[4], Integer.parseInt(args[5]));

        } catch (Exception e) {
            // System.err.println(e.toString());
        }

    }
}
