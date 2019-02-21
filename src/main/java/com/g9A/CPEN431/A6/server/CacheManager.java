package com.g9A.CPEN431.A6.server;

import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;

import java.util.concurrent.TimeUnit;

class CacheManager {
    private static CacheManager ourInstance = new CacheManager();

    private Cache<ByteString, KeyValueResponse.KVResponse> requestCache;

    static CacheManager getInstance() {
        return ourInstance;
    }

    private CacheManager() {
        this.requestCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build();
    }

    void Put(ByteString uuid, KeyValueResponse.KVResponse response) {
        this.requestCache.put(uuid, response);
    }

    /**
     * Get the response for this uuid
     * @param uuid message identifier
     * @return cached response or null if it is not cached
     */
    KeyValueResponse.KVResponse Get(ByteString uuid) {
        return this.requestCache.getIfPresent(uuid);
    }

    long Size() {
        return this.requestCache.size();
    }
}
