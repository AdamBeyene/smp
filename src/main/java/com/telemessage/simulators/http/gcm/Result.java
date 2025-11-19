package com.telemessage.simulators.http.gcm;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

@Setter
@Getter
public class Result implements Serializable, GCMResult {
    protected String messageId;
    protected String errorCode;

    public Result(String errorCode) {
        this.errorCode = errorCode;
    }

    public Result(String messageId, String errorCode) {
        this.messageId = messageId;
        this.errorCode = errorCode;
    }

    public String toString() {
        return "{" +
        "\"success\": " + (StringUtils.isEmpty(errorCode) ? "1" : "0") + "," +
        "\"failure\": " + (!StringUtils.isEmpty(errorCode) ? errorCode : "0") + "," +
        (!StringUtils.isEmpty(errorCode) ? "" : "\"message_id\": \"" + messageId + "\",") +
        "\"canonical_ids\": 1" +
        "}";
    }

}

