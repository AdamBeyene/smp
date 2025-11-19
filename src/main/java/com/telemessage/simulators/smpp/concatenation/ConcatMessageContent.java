package com.telemessage.simulators.smpp.concatenation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConcatMessageContent {
    private String messageText;
    private byte[] rawContent;
    private boolean success;
    private String error;
}