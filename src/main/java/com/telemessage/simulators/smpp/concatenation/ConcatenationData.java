package com.telemessage.simulators.smpp.concatenation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * @author ronen
 * @since 26/09/2021
 */
@Data
@AllArgsConstructor
@ToString
public class ConcatenationData {

    ConcatenationType concatenationType;
    int concatenatedMessageId;
    int concatenatedMessageSize;
    int segmentIndex;
}
