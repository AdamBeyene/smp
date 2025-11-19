package com.telemessage.simulators.http;

import com.telemessage.simulators.web.wrappers.AbstractMessage;
import com.telemessage.simulators.web.wrappers.HttpParam;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Getter
@Slf4j
public class DeliveryReceiptHttpMessage extends AbstractMessage {

    String msgId;
    int status;
    HttpParam[] params;

    public DeliveryReceiptHttpMessage() {
    }

    public DeliveryReceiptHttpMessage(String msgId, int status) {
        this.msgId = msgId;
        this.status = status;
    }

    @Override
    protected String getText() {
        return String.valueOf(getStatus());
    }

}
