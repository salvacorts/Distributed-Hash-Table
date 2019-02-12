package com.s91291682.CPEN431.A3.server;

import java.io.IOException;

import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;

public class Main {
	private static HTTPServer promServer;
	private static MetricsThread metrics;

	protected static Gauge totalKeys = Gauge.build()
     .name("keys").help("total keys in storage").register();;


    public static void main(String[] args) throws IOException {

        if (args.length != 2) {
            // System.err.println("Error: Missing parameters!");
            // System.err.println("java -jar A3.jar <server port>");
            return;
        }

		promServer = new HTTPServer(Integer.parseInt(args[1]));
		
		metrics = new MetricsThread();
		metrics.start();
		
    	totalKeys.set(0);

        try {
            Server server = new Server(Integer.parseInt(args[0]));
            server.StartServing();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
