package com.g9A.CPEN431.A11.server.kvMap;

import java.util.Arrays;

import com.g9A.CPEN431.A11.utils.StringUtils;
import com.google.protobuf.ByteString;

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

    public int getHash() {
        return Math.floorMod(ByteString.copyFrom(key).hashCode(), 256);
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
