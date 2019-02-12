package com.s91291682.CPEN431.A3.server;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import com.sun.management.OperatingSystemMXBean;

import io.prometheus.client.Gauge;

public class MetricsThread implements Runnable{
	private Thread t;
	private boolean stopflag = false;
	
	final Gauge cpuUsage = Gauge.build()
		     .name("cpu").help("cpu usage").register();
	final Gauge memUsage = Gauge.build()
		     .name("mem").help("memory usage").register();
	
	
	OperatingSystemMXBean op;
	MemoryMXBean memBean;
	
	
	MetricsThread(){
		op = (OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
		memBean = ManagementFactory.getMemoryMXBean();
	}
	
	public void run() {
		
		memUsage.set(memBean.getHeapMemoryUsage().getUsed());
		
		cpuUsage.set(100*op.getProcessCpuLoad());
        
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if(!stopflag) {
			this.run();
		}
	}
	
	public void start() {
		stopflag = false;
		
		if(t == null) {
			t = new Thread(this);
			t.start();
		}
	}
	
	public void stop() {
		stopflag = true;
	}
}
