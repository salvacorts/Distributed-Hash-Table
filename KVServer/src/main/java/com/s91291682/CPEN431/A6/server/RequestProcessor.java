package com.s91291682.CPEN431.A6.server;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import io.prometheus.client.Gauge;

import com.google.protobuf.ByteString;
import com.s91291682.CPEN431.A6.server.exceptions.*;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class RequestProcessor {
	
    private static RequestProcessor ourInstance = new RequestProcessor();

    private ConcurrentHashMap<ByteString, KVMapValue> kvMap = new ConcurrentHashMap<ByteString, KVMapValue>();  // Key value map with mutex

    /**
     * Process a PUT request updating the kvMap
     * @param request request to process
     * @return response with error code, or success with value
     */
    private KeyValueResponse.KVResponse DoPut(KeyValueRequest.KVRequest request) throws MissingParameterException,
                                                                                               KeyTooLargeException,
                                                                                               ValueTooLargeException,
                                                                                               OutOfSpaceException {
        if (!request.hasKey() || !request.hasValue() || !request.hasVersion()) throw new MissingParameterException();

        if (request.getKey().size() > 32) throw new KeyTooLargeException();

        if (request.getValue().size() > 10000) throw new ValueTooLargeException();

        // Check if there is enough space to store this data, leaving at least space for another biggest request
        long storeSize = request.getKey().size() + request.getValue().size();

        if ((Runtime.getRuntime().freeMemory() - storeSize) <  7864320) throw new OutOfSpaceException();

        KVMapValue value = new KVMapValue(request.getValue(), request.getVersion());

        kvMap.put(request.getKey(), value);
        
        Main.totalKeys.inc();

        return KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(0)
                .build();
    }

    /**
     * Process a GET request searching on the kvMap
     * @param request message request
     * @return a response containing an error or a value
     */
    private KeyValueResponse.KVResponse DoGet(KeyValueRequest.KVRequest request) throws KeyTooLargeException,
                                                                                               MissingParameterException,
                                                                                               UnexistingKey {
        if (!request.hasKey()) throw new MissingParameterException();

        if (request.getKey().size() > 32) throw new KeyTooLargeException();

        if (!kvMap.containsKey(request.getKey())) throw new UnexistingKey();

        KVMapValue value = kvMap.get(request.getKey());

        return KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(0)
                .setValue(value.getValue())
                .setVersion(value.getVersion())
                .build();
    }

    /**
     * Process a DELETE request updating the kvMap
     * @param request message request
     * @return a response containing an error (actual error or success)
     */
    private KeyValueResponse.KVResponse DoDelete(KeyValueRequest.KVRequest request) throws KeyTooLargeException,
                                                                                                  MissingParameterException,
                                                                                                  UnexistingKey {
        if (!request.hasKey()) throw new MissingParameterException();

        if (request.getKey().size() > 32) throw new KeyTooLargeException();

        if (!kvMap.containsKey(request.getKey())) throw new UnexistingKey();

        kvMap.remove(request.getKey());

        Main.totalKeys.dec();

        return KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(0)
                .build();
    }

    /**
     * Process a WIPEOUT request removing all elements in the kvMap
     * @return a response containing an error (actual error or success)
     */
    private KeyValueResponse.KVResponse DoWipeout() {
        kvMap.clear();
        Main.totalKeys.set(0);

        return KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(0)
                .build();
    }

    /**
     * Process a ISALIVE request
     * @return response with success code
     */
    private KeyValueResponse.KVResponse DoIsAlive() {
        return KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(0)
                .build();
    }

    /**
     * Process a GETPID request
     * @return process PID
     */
    private KeyValueResponse.KVResponse DoGetPID() {
        int pid = Integer.parseInt(ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);

        return KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(0)
                .setPid(pid)
                .build();
    }

    /**
     * Process a GetMembershipCount request
     * @return 1 (just for now)
     */
    private KeyValueResponse.KVResponse DoGetMembershipCount() {
        int count = 1;

        return KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(0)
                .setMembershipCount(count)
                .build();
    }

    static RequestProcessor getInstance() {
        return ourInstance;
    }

    KeyValueResponse.KVResponse ProcessRequest(KeyValueRequest.KVRequest request) throws ShutdownCommandException {
        KeyValueResponse.KVResponse response;


        try {
            switch (request.getCommand()) {
                case 1:
                    // System.out.println("PUT received");
                    response = DoPut(request);
                    break;
                case 2:
                    // System.out.println("Get received");
                    response = DoGet(request);
                    break;
                case 3:
                    // System.out.println("Remove received");
                    response = DoDelete(request);
                    break;
                case 4:
                    // System.out.println("Shutdown received");
                    throw new ShutdownCommandException();
                case 5:
                    // System.out.println("Wipeout received");
                    response = DoWipeout();
                    break;
                case 6:
                    // System.out.println("IsAlive received");
                    response = DoIsAlive();
                    break;
                case 7:
                    // System.out.println("GetPID received");
                    response = DoGetPID();
                    break;
                case 8:
                    // System.out.println("GetMembershipCount received");
                    response = DoGetMembershipCount();
                    break;
                default:
                    // System.err.println("Unrecognized command received");
                    response = KeyValueResponse.KVResponse.newBuilder()
                            .setErrCode(5)
                            .build();
                    break;
            }
        } catch (KeyTooLargeException e) {
            // System.err.println(e.toString());

            return KeyValueResponse.KVResponse.newBuilder().setErrCode(6).build();

        } catch (ValueTooLargeException e) {
            // System.err.println(e.toString());

            return KeyValueResponse.KVResponse.newBuilder().setErrCode(7).build();

        } catch (MissingParameterException e) {
            // System.err.println(e.toString());

            return KeyValueResponse.KVResponse.newBuilder().setErrCode(21).build();
        } catch (UnexistingKey e) {
            // System.err.println(e.toString());

            return KeyValueResponse.KVResponse.newBuilder().setErrCode(1).build();
        } catch (OutOfSpaceException e) {
            // System.err.println(e.toString());

            System.gc();    // Run garbage collector

            return KeyValueResponse.KVResponse.newBuilder().setErrCode(2).build();
        }

        return response;
    }
}
