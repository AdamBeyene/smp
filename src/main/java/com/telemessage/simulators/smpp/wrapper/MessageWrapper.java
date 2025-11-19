package com.telemessage.simulators.smpp.wrapper;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class MessageWrapper {
    protected String serviceType;
    protected AddressWrapper source;
    protected AddressWrapper destination;
    protected byte dataCoding;
    protected String shortMessage;
    protected String payload;
    protected byte emseClass;
    protected String encoding;
    protected String callback;
    protected UdhiWrapper udhi;
    protected List<OptionalParameterWrapper> optional;

}
