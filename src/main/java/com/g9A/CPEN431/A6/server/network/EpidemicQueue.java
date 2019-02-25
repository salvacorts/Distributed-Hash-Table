package com.g9A.CPEN431.A6.server.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.g9A.CPEN431.A6.server.ServerNode;

public class EpidemicQueue implements Runnable {
	
	private boolean stopflag = false;
	private Thread t;
	
	private static List<Epidemic> queue;
	private static EpidemicQueue instance = new EpidemicQueue();
	
	private EpidemicQueue(){
		queue = Collections.synchronizedList(new ArrayList<Epidemic>());
	}
	
	public static EpidemicQueue getInstance() {
		return instance;
	}
	
	public static int generateId() {
		boolean found = false;
		int id;
		Random rand = new Random();
		do {
			id = rand.nextInt();
			found = true;
			for(Epidemic e: queue) {
				if(e.epId == id) {
					found = false;
					break;
				}
			}
		}while(!found);
		return id;
	}
	
	public void add(Epidemic epi) {
		for(Epidemic e: queue) {
			if(e.epId == epi.epId) {
				System.out.println("epidemic already in queue");
				return;
			}
		}
		queue.add(epi);
	}
	
	public void run() {
		if(!queue.isEmpty()) {
			Epidemic epi = queue.get(0);
			epi.start();
			queue.remove(0);
		}
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if(!stopflag) this.run();
	}
	
	public void start() {
        stopflag = false;

        if (t == null) {
            t = new Thread(this);
            t.start();
        }
	}
	
	public void stop() {
		stopflag = true;
	}
}
