package com.g9A.CPEN431.A12.server.cache;

import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;

import java.util.concurrent.TimeUnit;

public class CacheManager {
    private static final CacheManager ourInstance = new CacheManager();

    private Cache<ByteString, KeyValueResponse.KVResponse> requestCache;

    public static CacheManager getInstance() {
        return ourInstance;
    }

    private CacheManager() {
        this.requestCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build();
    }

    public void Put(ByteString uuid, KeyValueResponse.KVResponse response) {
        this.requestCache.put(uuid, response);
    }

    /**
     * Get the response for this uuid
     * @param uuid message identifier
     * @return cached response or null if it is not cached
     */
    public KeyValueResponse.KVResponse Get(ByteString uuid) {
        return this.requestCache.getIfPresent(uuid);
    }

    public long Size() {
        return this.requestCache.size();
    }
}
