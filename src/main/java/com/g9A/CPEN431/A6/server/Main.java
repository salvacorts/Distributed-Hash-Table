package com.g9A.CPEN431.A6.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import com.g9A.CPEN431.A6.server.metrics.MetricsServer;

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
        
        ServerNode[] nodes;
		try {
			FileReader fileReader = 
	                new FileReader("./nodes-list.txt");
            
	        BufferedReader bufferedReader = 
	                new BufferedReader(fileReader);
	        String line = null;
	        int i = 0;
	        while((line = bufferedReader.readLine()) != null) {
	        	i++;
	        }
	        nodes = new ServerNode[i];
	        bufferedReader.close();
	        fileReader = new FileReader("./nodes-list.txt");
	        bufferedReader = new BufferedReader(fileReader);
	        i = 0;
	        while((line = bufferedReader.readLine()) != null) {
	        	nodes[i] = new ServerNode(line);
	        	i++;
	        }

	        bufferedReader.close();   
		}
		catch(IOException ex){
			System.out.println("nodes-list.txt file not found or has invalid format");
			throw ex;
		}

        // Run prometheus Metrics
        HTTPServer promServer = new HTTPServer(Integer.parseInt(args[1]));
        DefaultExports.initialize();
        metrics = MetricsServer.getInstance();
		metrics.start();

        try {
        	
            Server server = new Server(Integer.parseInt(args[0]), nodes);
            server.StartServing();
        } catch (Exception e) {
            e.printStackTrace();
        }
        metrics.stop();
    }
}
