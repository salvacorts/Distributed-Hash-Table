package com.g9A.CPEN431.A7.server;

import org.jetbrains.annotations.NotNull;

import java.net.DatagramSocket;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

class WorkerThreadFactory implements ThreadFactory {
    private DatagramSocket[] sockets;
    private int nextSocketAssigned;

    WorkerThreadFactory(int coresAvailable) throws java.net.SocketException {
        this.nextSocketAssigned = 0;

        sockets = new DatagramSocket[coresAvailable];

        for (int i = 0; i < sockets.length; i++) {
            sockets[i] = new DatagramSocket();
        }
    }

    synchronized DatagramSocket GetSocketToUse() {
        return sockets[this.nextSocketAssigned];
    }

    synchronized void dispose() {
        for (DatagramSocket s : sockets) {
            s.close();
        }
    }

    public Thread newThread(@NotNull Runnable r) {
        // Create a thread for the worker to be launched
        Thread t = new Thread(r);

        // Set worker priority
        t.setPriority(Thread.MIN_PRIORITY);

        // Round robin in socket assignation
        nextSocketAssigned = (nextSocketAssigned + 1) % sockets.length;

        return t;
    }
}
