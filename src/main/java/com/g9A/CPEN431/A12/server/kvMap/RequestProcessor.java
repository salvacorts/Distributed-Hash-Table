package com.g9A.CPEN431.A12.server.kvMap;

import ca.NetSysLab.ProtocolBuffers.InternalRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueRequest;
import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;

import com.g9A.CPEN431.A12.server.Server;
import com.g9A.CPEN431.A12.server.ServerNode;
import com.g9A.CPEN431.A12.server.exceptions.*;
import com.g9A.CPEN431.A12.server.metrics.MetricsServer;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RequestProcessor {
	
    private static final RequestProcessor ourInstance = new RequestProcessor();

    private final MetricsServer metrics = MetricsServer.getInstance();

    public ConcurrentHashMap<KVMapKey, KVMapValue> kvMap = new ConcurrentHashMap<KVMapKey, KVMapValue>();  // Key value map with mutex
    
    /**
     * Generates a hash value of a ByteString in the range [0,255]
     * @param key the key to hash
     * @return
     */
    public static int getHash(ByteString key) {
    	return Math.floorMod(key.hashCode(), 256);
    }
    public static int getHash(KVMapKey key) {
    	return getHash(ByteString.copyFrom(key.getKey()));
    }
    
    /**
     * Indicates if the request's key places it in the current node
     * @param request the given request
     * @return the correct node to reroute to
     * @throws MissingParameterException
     */
    private static ServerNode CorrectNode(KeyValueRequest.KVRequest request) throws MissingParameterException {
    	if (!request.hasKey()) throw new MissingParameterException();

        int hash = getHash(request.getKey());
        
        int original = hash;
		hash = (hash+1)%256;
    	while(hash != original) {
    		ServerNode node = Server.HashCircle.get(hash);
    		if(node != null) {
    			return node;
    		}
    		hash = (hash+1)%256;
    	}
    	return Server.selfNode;
    }
    
	/**
     * Puts a new set of keys
     * @param hashStart
     * @param hashEnd
     * @return
     */
	public KeyValueResponse.KVResponse MassPut(InternalRequest.KVTransfer request) {
		request.getKvlistList().forEach(pair -> {
			KVMapKey key = new KVMapKey(pair.getKey().toByteArray());
			KVMapValue value = new KVMapValue(pair.getValue().toByteArray(), pair.getVersion());
			kvMap.put(key, value);
		});
		return KeyValueResponse.KVResponse.newBuilder()
				.setErrCode(0)
				.build();
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

        if (request.getKey().size() > 32) throw new KeyTooLargeException();

        if (request.getValue().size() > 10000) throw new ValueTooLargeException();
        
        if(!request.hasReps()) {
            ServerNode correctNode = CorrectNode(request);
        	if(!correctNode.equals(Server.selfNode)) throw new WrongNodeException(correctNode);
        }

        // Check if there is enough space to store this data, leaving at least space for another biggest request
        long storeSize = request.getKey().size() + request.getValue().size();
        long totalFree = Runtime.getRuntime().maxMemory() - (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());

        // Set 7MB free
        if ((totalFree - storeSize) < 7340032) throw new OutOfSpaceException();

        KVMapValue value = new KVMapValue(request.getValue().toByteArray(), request.getVersion());
        KVMapKey key = new KVMapKey(request.getKey().toByteArray());

        if (!kvMap.containsKey(key)) metrics.keysStored.inc();

        kvMap.put(key, value);
        
        //System.out.println("PUT with key " + request.getKey().hashCode() + ", value " + request.getValue().hashCode());

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

        if (request.getKey().size() > 32) throw new KeyTooLargeException();
        
        ServerNode correctNode = CorrectNode(request);
        if(!correctNode.equals(Server.selfNode)) {
        	throw new WrongNodeException(correctNode);
        }

        KVMapKey key = new KVMapKey(request.getKey().toByteArray());

        if (!kvMap.containsKey(key)) {
            throw new UnexistingKey();
        }
        
        KVMapValue value = kvMap.get(key);
        
        //System.out.println("GET with key " + request.getKey().hashCode() + ", value " + value.getValue().hashCode());

        return KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(0)
                .setValue(ByteString.copyFrom(value.getValue()))
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
        
        if (request.getKey().size() > 32) throw new KeyTooLargeException();

        if(!request.hasReps()) {
        	ServerNode correctNode = CorrectNode(request);
        	if(!correctNode.equals(Server.selfNode)) throw new WrongNodeException(correctNode);
        }
        
        KVMapKey key = new KVMapKey(request.getKey().toByteArray());

        if (!kvMap.containsKey(key)) throw new UnexistingKey();

        kvMap.remove(key);

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
        System.out.println("[-] Wipeout: " + kvMap.size() + " removed" );

        kvMap.clear();
        kvMap = new ConcurrentHashMap<KVMapKey, KVMapValue>();
        
        metrics.keysStored.set(0);

        System.gc();    // Clear the previous kvMap

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
     * @return number of active nodes
     */
    private KeyValueResponse.KVResponse DoGetMembershipCount() {
        int count = Server.ServerNodes.size();

        return KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(0)
                .setMembershipCount(count)
                .build();
    }
    
    /**
     * Process a GetMembershipList request
     * @return KVResponse with string of active servers
     */
    private KeyValueResponse.KVResponse DoGetMembershipList(){
    	String list = "";
    	for(ServerNode node: Server.ServerNodes) {
    		list += node.getAddress().getHostName() + ':' + node.getPort() + '\n';
    	}
    	ByteString value = ByteString.copyFromUtf8(list);
    	return KeyValueResponse.KVResponse.newBuilder()
                .setErrCode(0)
                .setValue(value)
                .build();
    }

    public static RequestProcessor getInstance() {
        return ourInstance;
    }

    public KeyValueResponse.KVResponse ProcessRequest(KeyValueRequest.KVRequest request, ByteString messageId)
    		throws ShutdownCommandException, IOException, WrongNodeException, UnexistingKey {
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
                    //throw new ShutdownCommandException();
                    System.exit(0);
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
                case 22:
                	response = DoGetMembershipList();
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
