package com.g9A.CPEN431.A7.server.network;

import java.util.concurrent.TimeUnit;

import com.g9A.CPEN431.A7.server.cache.CacheManager;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;

import ca.NetSysLab.ProtocolBuffers.KeyValueResponse;

public class EpidemicCache {
    private static EpidemicCache ourInstance = new EpidemicCache();

    private Cache<Long, Integer> epiCache;

    public static EpidemicCache getInstance() {
        return ourInstance;
    }

    private EpidemicCache() {
        this.epiCache = CacheBuilder.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();
    }
    
    public boolean check(long epId) {
    	return epiCache.getIfPresent(epId) != null;
    }
    
    public void put(long epId) {
    	epiCache.put(epId, 0);
    }

    public long Size() {
        return this.epiCache.size();
    }
}
