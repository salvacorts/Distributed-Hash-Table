package com.s91291682.CPEN431.A6.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;

import com.s91291682.CPEN431.A6.server.metrics.MetricsServer;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

public class Main {
	private static MetricsServer metrics;

    public static void main(String[] args) throws IllegalArgumentException, IOException {
    	
        if (args.length != 2) {
            System.err.println("Error: Missing parameters!");
            System.err.println("java -jar A6.jar <server port> <metrics port>");
            return;
        }

        // Run prometheus Metrics
        HTTPServer promServer = new HTTPServer(Integer.parseInt(args[1]));
        DefaultExports.initialize();
        metrics = MetricsServer.getInstance();
		metrics.start();
		
		ServerNode[] nodes;
		
		try {
			FileReader fileReader = 
	                new FileReader("./nodes-list.txt");
            
	        BufferedReader bufferedReader = 
	                new BufferedReader(fileReader);
	        String[] lines = (String[])bufferedReader.lines().toArray();
	        nodes = new ServerNode[lines.length];
	        for(int i = 0; i < lines.length; i++) {
	        	ServerNode node = new ServerNode(lines[i]);
	        }
	        

	        bufferedReader.close();   
		}
		catch(IOException ex){
			System.out.println("nodes-list.txt file not found or has invalid format");
			return;
		}

        try {
        	
            Server server = new Server(Integer.parseInt(args[0]), nodes);
            server.StartServing();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
