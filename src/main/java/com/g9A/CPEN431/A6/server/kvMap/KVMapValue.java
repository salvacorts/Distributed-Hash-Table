package com.g9A.CPEN431.A6.server.kvMap;

import com.google.protobuf.ByteString;

public class KVMapValue {
    private ByteString value;
    private int version;

    public KVMapValue(ByteString value, int version) {
        this.value = value;
        this.version = version;
    }

    public ByteString getValue() {
        return value;
    }

    public int getVersion() {
        return version;
    }

    public void setValue(ByteString value) {
        this.value = value;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String toString() {
        return value.toStringUtf8()+":v"+version;
    }
}
