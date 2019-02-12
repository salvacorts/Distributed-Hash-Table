package com.s91291682.CPEN431.A3.server;

import com.google.protobuf.ByteString;

class KVMapValue {
    private ByteString value;
    private int version;

    KVMapValue(ByteString value, int version) {
        this.value = value;
        this.version = version;
    }

    ByteString getValue() {
        return value;
    }

    int getVersion() {
        return version;
    }

    void setValue(ByteString value) {
        this.value = value;
    }

    void setVersion(int version) {
        this.version = version;
    }
}
