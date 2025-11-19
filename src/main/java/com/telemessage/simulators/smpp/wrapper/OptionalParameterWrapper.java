package com.telemessage.simulators.smpp.wrapper;

import com.logica.smpp.pdu.tlv.TLV;
import com.logica.smpp.pdu.tlv.TLVShort;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OptionalParameterWrapper {
    protected String name;
    protected short tag;
    protected String value;

    public OptionalParameterWrapper() {}

    public OptionalParameterWrapper(String name, short tag, String value) {
        this.tag = tag;
        this.value = value;
        this.name = name;
    }

    public static OptionalParameterWrapper create(String name, TLV tlv) {
        try {
            if (tlv == null || tlv.getData() == null || tlv.getData().getBuffer() == null || tlv.getData().getBuffer().length <= 0)
                return null;

            String value = null;
            if (tlv instanceof TLVShort)
                value = String.valueOf(((TLVShort)tlv).getValue());
            else
                value = new String(tlv.getData().getBuffer());
            return new OptionalParameterWrapper(name, tlv.getTag(), value);
        } catch (Exception e) {
            return null;
        }

    }
}
