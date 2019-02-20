package com.s91291682.CPEN431.A6.server;

import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import com.google.protobuf.ByteString;
import com.s91291682.CPEN431.A6.client.Client;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerTest {
    private static Client client;

    @org.junit.BeforeClass
    public static void setUp() throws Exception {
        client = new Client("128.189.199.19", 10145, 3);

        // Launch server in a new thread
        new Thread(new Runnable() {
            public void run() {
                try {
                    Server server = new Server(10145, new ServerNode[]{
                    	new ServerNode("128.189.199.19", 10145, 0, 255)
                    });
                    server.StartServing();
                } catch (Exception e) {
                    // System.err.println(e.toString());
                }
            }
        }).start();
    }

    @org.junit.AfterClass
    public static void tearDown() throws Exception {
        client.DoRequest(4, "", "", 0); // shutdown
    }

    @Test
    public void testPut() throws Exception {
        KeyValueResponse.KVResponse expected = KeyValueResponse.KVResponse.newBuilder()
                                                    .setErrCode(0)
                                                .build();

        KeyValueResponse.KVResponse result = client.DoRequest(1, "foo", "bar", 1);
        assertEquals(expected, result);

        result = client.DoRequest(1, "a", "b", 1);
        assertEquals(expected, result);

        result = client.DoRequest(1, "rafael", "alberti", 1);
        assertEquals(expected, result);
    }

    /*@Test
    public void testPut_RunOutOfSpace() throws Exception {
        KeyValueResponse.KVResponse expected = KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(2)
                .build();

        KeyValueResponse.KVResponse result = null;
        String rndValue = RandomCreator.RandomString(10000);

        while (result == null || result.getErrCode() != 2) {
            String rndKey = RandomCreator.RandomString(32);

            result = client.DoRequest(1, rndKey, rndValue, 1);

            if (result.getErrCode() != 2) assertEquals(0, result.getErrCode());
        }

        assertNotNull(result);
        assertEquals(2, result.getErrCode());

        result =  client.DoRequest(5, "", "", 0);
        assertEquals(0, result.getErrCode());
    }/**/

    @Test
    public void testGet() throws Exception {
        KeyValueResponse.KVResponse expected = KeyValueResponse.KVResponse.newBuilder()
                    .setErrCode(0)
                    .setValue(ByteString.copyFromUtf8("bar"))
                    .setVersion(1)
                .build();

        testPut();  // We need to add those values

        KeyValueResponse.KVResponse result = client.DoRequest(2, "foo", "", 0);

        assertEquals(expected, result);
    }

    @Test
    public void testRemove() throws Exception {
        testPut();

        KeyValueResponse.KVResponse expected = KeyValueResponse.KVResponse.newBuilder()
                    .setErrCode(0)
                .build();

        KeyValueResponse.KVResponse result = client.DoRequest(3, "foo", "", 0);

        assertEquals(expected, result);

        expected = KeyValueResponse.KVResponse.newBuilder()
                    .setErrCode(1)
                .build();

        result = client.DoRequest(2, "foo", "", 0);

        assertEquals(expected, result);
    }

    @Test
    public void testWipeout() throws Exception {
        testPut();

        KeyValueResponse.KVResponse expected = KeyValueResponse.KVResponse.newBuilder()
                    .setErrCode(0)
                .build();

        KeyValueResponse.KVResponse result = client.DoRequest(5, "", "", 0);

        assertEquals(expected, result);
    }

    @Test
    public void testIsAlive() throws Exception {
        KeyValueResponse.KVResponse expected = KeyValueResponse.KVResponse.newBuilder()
                    .setErrCode(0)
                .build();

        KeyValueResponse.KVResponse result = client.DoRequest(6, "", "", 0);

        assertEquals(expected, result);
    }

    @Test
    public void testGetPid() throws Exception {
        KeyValueResponse.KVResponse result = client.DoRequest(7, "", "", 0);

        assertTrue(result.hasPid());
    }

    @Test
    public void testGetMembershipCount() throws Exception {
        KeyValueResponse.KVResponse result = client.DoRequest(8, "", "", 0);

        assertEquals(1, result.getMembershipCount());
    }
}