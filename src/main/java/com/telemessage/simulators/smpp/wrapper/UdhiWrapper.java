package com.telemessage.simulators.smpp.wrapper;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class UdhiWrapper {
    protected byte id;
    protected byte size;
    protected byte index;

    public UdhiWrapper(byte id, byte size, byte index) {
        this.id = id;
        this.size = size;
        this.index = index;
    }

    public UdhiWrapper() {}

    public static UdhiWrapper create(byte[] udhi) {
        if (udhi == null || udhi.length <= 6)
            return null;
        return new UdhiWrapper(udhi[3], udhi[4], udhi[5]);
    }
}
