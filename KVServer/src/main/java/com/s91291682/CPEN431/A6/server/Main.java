package com.s91291682.CPEN431.A6.server;

import java.io.IOException;

import com.s91291682.CPEN431.A6.server.metrics.MetricsServer;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

public class Main {
	private static MetricsServer metrics;

    public static void main(String[] args) throws IOException {

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

        try {
            Server server = new Server(Integer.parseInt(args[0]));
            server.StartServing();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
