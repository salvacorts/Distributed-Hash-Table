package com.g9A.CPEN431.A8.server.network;

import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;

public class EpidemicCache {
    private static final EpidemicCache ourInstance = new EpidemicCache();

    private Cache<ByteString, Byte> epiCache;

    public static EpidemicCache getInstance() {
        return ourInstance;
    }

    private EpidemicCache() {
        this.epiCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(240, TimeUnit.SECONDS)
                .build();
    }
    
    public boolean check(ByteString id) {
   		return epiCache.getIfPresent(id) != null;
    }
    
    public void put(ByteString id) {
    	epiCache.put(id, (byte) 0);
    }

    public long Size() {
        return this.epiCache.size();
    }
}
