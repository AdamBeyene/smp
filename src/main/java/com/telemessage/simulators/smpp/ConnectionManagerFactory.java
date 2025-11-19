package com.telemessage.simulators.smpp;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionManagerFactory {

    public static SMPPConnManager get(SMPPConnection.BindType bindType) {
        if (bindType == SMPPConnection.BindType.ESME)
            return new ESMEConnManager();
        else if (bindType == SMPPConnection.BindType.SMSC)
            return new SMSCConnManager();
        throw new IllegalArgumentException("Type " + bindType.name() + " is not supported");
    }
}