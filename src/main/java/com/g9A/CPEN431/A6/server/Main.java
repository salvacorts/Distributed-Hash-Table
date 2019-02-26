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

			try {
                nodes.add(new ServerNode(line,0,1));
            } catch (Exception e) {
			    e.printStackTrace();
            }
		}
		
		int total = nodes.size();
		for(int i = 0; i < total; i++) {
			int start = i == 0 ? 0 : i*255/total + 1;
			int end = (i+1)*255/total;
			nodes.get(i).setHashRange(start, end);
		}

		bufferedReader.close();

		return nodes;
	}

    public static void main(String[] args) throws IllegalArgumentException, IOException {
    	
        if (args.length != 4) {
            System.err.println("Error: Missing parameters!");
            System.err.println("java -jar A6.jar <server port> <metrics port> <epidemic port> <nodes list>");
            return;
        }

        // Run prometheus Metrics
        HTTPServer promServer = new HTTPServer(Integer.parseInt(args[1]));
        DefaultExports.initialize();
        metrics = MetricsServer.getInstance();
		metrics.start();

		FailureCheck fc = new FailureCheck();
		fc.start();

		// Run epidemic service

        try {
            List<ServerNode> nodes = LoadNodesFromFile(args[3]);

            try {
                Server server = new Server(Integer.parseInt(args[0]), Integer.parseInt(args[2]), nodes);
                server.StartServing();
            } catch (Exception e) {
                e.printStackTrace();
            }
            
        } catch (IOException ex){
            System.out.println("Node list file not found or has invalid format");
            throw ex;
        }

        metrics.stop();
    }
}
