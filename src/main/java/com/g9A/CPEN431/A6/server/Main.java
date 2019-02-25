package com.g9A.CPEN431.A6.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.g9A.CPEN431.A6.server.metrics.MetricsServer;
import com.g9A.CPEN431.A6.server.network.FailureCheck;

import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

public class Main {
	private static MetricsServer metrics;

	private static List<ServerNode> LoadNodesFromFile(String filename) throws IOException {
		List<ServerNode> nodes = 
				Collections.synchronizedList(new ArrayList<ServerNode>());

		FileReader fileReader = new FileReader(filename);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line = null;

		while ((line = bufferedReader.readLine()) != null) {
			String[] args = line.split(" ");

			String address = args[0];
			int port = Integer.parseInt(args[1]);
			int hashStart = Integer.parseInt(args[2]);
			int hashEnd = Integer.parseInt(args[3]);

			try {
                nodes.add(new ServerNode(address, port, hashStart, hashEnd));
            } catch (Exception e) {
			    e.printStackTrace();
            }
		}

		bufferedReader.close();

		return nodes;
	}

    public static void main(String[] args) throws IllegalArgumentException, IOException {
    	
        if (args.length != 3) {
            System.err.println("Error: Missing parameters!");
            System.err.println("java -jar A6.jar <server port> <metrics port> <nodes list>");
            return;
        }

        // Run prometheus Metrics
        HTTPServer promServer = new HTTPServer(Integer.parseInt(args[1]));
        DefaultExports.initialize();
        metrics = MetricsServer.getInstance();
		metrics.start();

		FailureCheck fc = new FailureCheck();
		fc.start();

        try {
            List<ServerNode> nodes = LoadNodesFromFile(args[2]);

            try {
                Server server = new Server(Integer.parseInt(args[0]), nodes);
                server.StartServing();
            } catch (Exception e) {
                e.printStackTrace();
            }
            
        } catch (IOException ex){
            System.out.println("nodes-list.txt file not found or has invalid format");
            throw ex;
        }

        metrics.stop();
    }
}
