package com.g9A.CPEN431.A10.server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.g9A.CPEN431.A10.server.metrics.MetricsServer;

import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

public class Main {

	private static List<ServerNode> LoadNodesFromFile(String filename) throws IOException {
		List<ServerNode> nodes = 
				Collections.synchronizedList(new ArrayList<ServerNode>());

		FileReader fileReader = new FileReader(filename);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line;
		int id = 0;

		while ((line = bufferedReader.readLine()) != null) {

			try {
				if (!line.trim().isEmpty()) {
					nodes.add(new ServerNode(line,id));
					id++;
				}
            } catch (Exception e) {
			    e.printStackTrace();
            }
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
        MetricsServer metrics = MetricsServer.getInstance();
		metrics.start();

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
