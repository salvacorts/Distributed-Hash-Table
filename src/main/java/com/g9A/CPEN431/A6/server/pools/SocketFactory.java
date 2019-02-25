package com.g9A.CPEN431.A6.server.pools;

import java.net.DatagramSocket;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;


public class SocketFactory extends BasePooledObjectFactory<DatagramSocket> {
    @Override
    public DatagramSocket create() throws Exception {
        return new DatagramSocket();
    }

    @Override
    public PooledObject<DatagramSocket> wrap(DatagramSocket socket) {
        return new DefaultPooledObject<DatagramSocket>(socket);
    }

    @Override
    public boolean validateObject(PooledObject<DatagramSocket> socket) {
        return !socket.getObject().isClosed();
    }

}
