package com.g9A.CPEN431.A10.server.pools;

import java.net.DatagramSocket;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

public class SocketPool extends GenericObjectPool<DatagramSocket> {
    public SocketPool(PooledObjectFactory<DatagramSocket> factory) {
        super(factory);
    }

    public SocketPool(PooledObjectFactory<DatagramSocket> factory, GenericObjectPoolConfig config) {
        super(factory, config);
    }
}
