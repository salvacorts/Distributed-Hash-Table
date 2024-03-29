package com.g9A.CPEN431.A12.server.metrics;


import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import com.sun.management.OperatingSystemMXBean;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public class MetricsServer implements Runnable {

    private static MetricsServer ourInstance = new MetricsServer();

    private Thread t;
    private boolean stopflag = false;

    private final Gauge cpuUsage = Gauge.build()
            .name("cpu").help("cpu usage").register();

    private final Gauge memUsage = Gauge.build()
            .name("mem").help("memory usage").register();

    private OperatingSystemMXBean op;
    private MemoryMXBean memBean;

    public final Gauge keysStored = Gauge.build()
            .name("keys").help("keys stored").register();
    public final Counter deadEpidemics = Counter.build()
    		.name("deadEpidemics").help("total epidemics started").register();

    public final Counter deadMessagesReceieved = Counter.build()
            .name("deadReceived").help("total epidemic messages").register();
    
    public final Counter aliveEpidemics = Counter.build()
    		.name("aliveEpidemics").help("total epidemics started").register();

    public final Counter aliveMessagesReceieved = Counter.build()
            .name("aliveReceived").help("total epidemic messages").register();
    
    public final Gauge deadNodes = Gauge.build()
    		.name("deadnodes").help("total number of dead nodes").register();

    private MetricsServer(){
        op = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        memBean = ManagementFactory.getMemoryMXBean();
        keysStored.set(0);
    }

    public static MetricsServer getInstance() {
        return ourInstance;
    }

    public void run() {

        memUsage.set(memBean.getHeapMemoryUsage().getUsed());
        cpuUsage.set(100*op.getProcessCpuLoad());

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
