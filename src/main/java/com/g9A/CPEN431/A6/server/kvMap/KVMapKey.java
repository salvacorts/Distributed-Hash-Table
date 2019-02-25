package com.g9A.CPEN431.A6.server.kvMap;

import com.g9A.CPEN431.A6.utils.StringUtils;

import java.util.Arrays;

public class KVMapKey {
    private byte[] key;

    public KVMapKey(byte[] key) {
        this.key = key;
    }

    public KVMapKey(KVMapKey other) {
        this.key = Arrays.copyOf(other.key, other.key.length);
    }

    public byte[] getKey() {
        return key;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(key);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof KVMapKey)) return false;

        return Arrays.equals(key, ((KVMapKey) obj).key);
    }

    @Override
    public String toString() {
        return StringUtils.byteArrayToHexString(key);
    }
}
