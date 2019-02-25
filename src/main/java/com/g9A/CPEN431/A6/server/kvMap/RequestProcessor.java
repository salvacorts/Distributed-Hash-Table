package com.g9A.CPEN431.A6.server.kvMap;

import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import com.g9A.CPEN431.A6.server.Server;

import com.g9A.CPEN431.A6.server.exceptions.*;
import com.g9A.CPEN431.A6.server.metrics.MetricsServer;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ConcurrentHashMap;

public class RequestProcessor {
	
    private static RequestProcessor ourInstance = new RequestProcessor();

    private ConcurrentHashMap<ByteString, KVMapValue> kvMap = new ConcurrentHashMap<ByteString, KVMapValue>();  // Key value map with mutex
    private MetricsServer metrics = MetricsServer.getInstance();

    /**
     * Indicates if the request's key places it in the current node
     * @param request the given request
     * @return whether the request's key is in this node's keyspace
     * @throws MissingParameterException
     */
    private static boolean CorrectNode(KeyValueRequest.KVRequest request) throws MissingParameterException {
    	if (!request.hasKey()) throw new MissingParameterException();

        int hash = Math.floorMod(request.getKey().hashCode(), 256);

    	return Server.selfNode.inSpace(hash);
    }
    
    /**
     * Process a PUT request updating the kvMap
     * @param request request to process
     * @return response with error code, or success with value
     */
    private KeyValueResponse.KVResponse DoPut(KeyValueRequest.KVRequest request) throws MissingParameterException,
                                                                                               KeyTooLargeException,
                                                                                               ValueTooLargeException,
                                                                                               OutOfSpaceException,
                                                                                               WrongNodeException {
        if (!request.hasKey() || !request.hasValue() || !request.hasVersion()) throw new MissingParameterException();

        if (!CorrectNode(request)) throw new WrongNodeException(Math.floorMod(request.getKey().hashCode(), 256));

        if (request.getKey().size() > 32) throw new KeyTooLargeException();

        if (request.getValue().size() > 10000) throw new ValueTooLargeException();

        // Check if there is enough space to store this data, leaving at least space for another biggest request
        long storeSize = request.getKey().size() + request.getValue().size();
        long totalFree = Runtime.getRuntime().maxMemory() - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());

        // Set 5MB free
        if ((totalFree - storeSize) < 5242880) throw new OutOfSpaceException();

        KVMapValue value = new KVMapValue(request.getValue(), request.getVersion());

        if(!kvMap.containsKey(request.getKey())) {
            metrics.keysStored.inc();
        }
        kvMap.put(request.getKey(), value);

        System.out.println("PUT with key " + request.getKey().hashCode() + ", value " + value.getValue().hashCode());

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
                                                                                               UnexistingKey,
                                                                                               WrongNodeException {
        if (!request.hasKey()) throw new MissingParameterException();

        if (!CorrectNode(request)) throw new WrongNodeException(Math.floorMod(request.getKey().hashCode(), 256));

        if (request.getKey().size() > 32) throw new KeyTooLargeException();

        if (!kvMap.containsKey(request.getKey())) throw new UnexistingKey();

        KVMapValue value = kvMap.get(request.getKey());
        
        System.out.println("GET with key " + request.getKey().hashCode() + ", value " + value.getValue().hashCode());

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
                                                                                                  UnexistingKey,
                                                                                                  WrongNodeException {
        if (!request.hasKey()) throw new MissingParameterException();

        if (!CorrectNode(request)) throw new WrongNodeException(Math.floorMod(request.getKey().hashCode(), 256));

        if (request.getKey().size() > 32) throw new KeyTooLargeException();

        if (!kvMap.containsKey(request.getKey())) throw new UnexistingKey();
        
        kvMap.remove(request.getKey());

        metrics.keysStored.dec();

        System.out.println("DELETE with key " + request.getKey().hashCode());

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
        metrics.keysStored.set(0);

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

    public static RequestProcessor getInstance() {
        return ourInstance;
    }

    public KeyValueResponse.KVResponse ProcessRequest(KeyValueRequest.KVRequest request, ByteString messageId)
    		throws ShutdownCommandException, IOException, WrongNodeException {
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
                    System.out.println("Wipeout received");
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
                    response = KeyValueResponse.KVResponse.newBuilder().setErrCode(5).build();
                    break;
            }
        } catch (KeyTooLargeException e) {
            e.printStackTrace();

            return KeyValueResponse.KVResponse.newBuilder().setErrCode(6).build();

        } catch (ValueTooLargeException e) {
            e.printStackTrace();

            return KeyValueResponse.KVResponse.newBuilder().setErrCode(7).build();

        } catch (MissingParameterException e) {
            e.printStackTrace();

            return KeyValueResponse.KVResponse.newBuilder().setErrCode(21).build();
        } catch (UnexistingKey e) {
            e.printStackTrace();

            return KeyValueResponse.KVResponse.newBuilder().setErrCode(1).build();
        } catch (OutOfSpaceException e) {
            e.printStackTrace();

            System.gc();    // Run garbage collector

            return KeyValueResponse.KVResponse.newBuilder().setErrCode(2).build();
        }

        return response;
    }
}
