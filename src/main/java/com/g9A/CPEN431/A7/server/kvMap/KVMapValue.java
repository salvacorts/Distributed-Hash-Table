package com.g9A.CPEN431.A7.server.kvMap;

import com.google.protobuf.ByteString;

import java.util.Arrays;

public class KVMapValue {
    private byte[] value;
    private int version;

    public KVMapValue(byte[] value, int version) {
        this.value = Arrays.copyOf(value, value.length);
        this.version = version;
    }

    public KVMapValue(KVMapValue other) {
        this.value = Arrays.copyOf(other.value, other.value.length);
        this.version = other.version;
    }

    public byte[] getValue() {
        return value;
    }

    public int getVersion() {
        return version;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String toString() {
        return ""+":v"+version;
    }
}
