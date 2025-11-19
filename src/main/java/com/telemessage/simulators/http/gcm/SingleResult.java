package com.telemessage.simulators.http.gcm;

public class SingleResult extends Result {

    public SingleResult(String errorCode, String messageId) {
        super(errorCode, messageId);
    }

    public SingleResult(String errorCode) {
        super(errorCode);
    }

    public String toString() {
        return "{\"results\" : [" + super.toString() + "]}";
    }
}
