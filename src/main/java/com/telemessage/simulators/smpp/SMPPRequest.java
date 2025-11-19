package com.telemessage.simulators.smpp;

import com.telemessage.simulators.web.wrappers.ShortMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Slf4j
public class SMPPRequest {

    public enum ConcatenationType {
        NA, PAYLOAD, UDHI, SAR, PAYLOAD_MESSAGE, UDHI_PAYLOAD;
    }

    public SMPPRequest() {
    }

    public SMPPRequest(String src, String dst, String serviceType, String text, String callback) {
        this.callback = callback;
        this.dst = dst;
        this.serviceType = serviceType;
        this.src = src;
        this.text = text;
    }

    public SMPPRequest(String src, String dst, String text, String serviceType, String callback,
                       Short userMessageRef, String srcSubAddress, String dstSubAddress,
                       String scheduleDeliveryTime, ShortMessage.Message_state_enum messageState , List<Map<String, String>> params , List<Long> partsDelay) {
        this.text = text;
        this.src = src;
        this.dst = dst;
        this.callback = callback;
        this.serviceType = serviceType;
        this.userMessageRef = userMessageRef;
        this.srcSubAddress = srcSubAddress;
        this.dstSubAddress = dstSubAddress;
        this.scheduleDeliveryTime = scheduleDeliveryTime;
        this.messageState = messageState;
        this.params = params;
        this.partsDelay = partsDelay;

    }

    protected String text;
    protected String src;
    protected String dst;
    protected String callback;
    @Setter
    protected String serviceType;
    protected Short userMessageRef;
    protected String srcSubAddress;
    protected String dstSubAddress;
    @Setter
    protected String scheduleDeliveryTime;
    @Setter
    protected List<Map<String, String>> params = new ArrayList<>();
    @Setter
    protected List<Long> partsDelay = new ArrayList<>();
    @Setter
    protected ShortMessage.Message_state_enum messageState;

    public SMPPRequest setCallback(String callback) { this.callback = callback;  return this; }

    public SMPPRequest setDst(String dst) { this.dst = dst;  return this; }

    public SMPPRequest setSrc(String src) { this.src = src;  return this; }

    public SMPPRequest setText(String text) { this.text = text;  return this; }

    public SMPPRequest setUserMessageRef(Short userMessageRef) { this.userMessageRef = userMessageRef; return this;  }

    public SMPPRequest setSrcSubAddress(String srcSubAddress) { this.srcSubAddress = srcSubAddress; return this;  }

    public SMPPRequest setDstSubAddress(String dstSubAddress) { this.dstSubAddress = dstSubAddress; return this;  }

    @Override
    public String toString() {
        final JSONObject json = new JSONObject();
        if (!StringUtils.isEmpty(text))
            json.put("text", text);
        if (!StringUtils.isEmpty(src))
            json.put("src", src);
        if (!StringUtils.isEmpty(dst))
            json.put("dst", dst);
        if (!StringUtils.isEmpty(callback))
            json.put("callback", callback);
        if (!StringUtils.isEmpty(serviceType))
            json.put("serviceType", serviceType);
        if (!params.isEmpty())
            json.put("params", params);
        if (!partsDelay.isEmpty())
            json.put("partsDelay", partsDelay);
        return json.toJSONString();
    }
}
