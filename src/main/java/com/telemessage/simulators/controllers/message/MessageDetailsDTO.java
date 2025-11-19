package com.telemessage.simulators.controllers.message;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for returning rich message details 
 * including binary data and encoding information
 */
@Data
@NoArgsConstructor
public class MessageDetailsDTO {
    private MessagesObject message;
    private boolean hasBinaryData;
    private String encoding;
    private int binaryDataSize;
}
