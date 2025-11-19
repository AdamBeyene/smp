package com.telemessage.simulators.web.wrappers;

import com.telemessage.simulators.smpp.SMPPConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Slf4j
public class DeliveryReceiptShortMessage extends AbstractMessage {


    public enum DRs {
        SUCCESS,ANSWRMC,NOANSER,FAXMACH,BLOCKED,PREMB,ILLEGAL,NOBILL,UNKNOWN,
        DELIVRD,ACCEPTD,ACKED,EXPIRED,DELETED,UNDELIV,REJECTD,FAILED,UNDELVR
    }

    @Schema(description = "Timestamp TimeMillis", example = "251015100000")
    long time;
    @Getter
    DRs status;
    @Getter
    @Schema(description = "DR's providerResult", example = "10179C64E86")
    String providerResult;
    @Getter
    @Schema(description = "Message type of service for the message [default SMS]", example = "SMS/EMS...")
    String serviceType;

    public long getTime() {
        return time > 0 ? time : System.currentTimeMillis();
    }

    @Override
    public String getText() {
        // build a human‐readable DR summary
        return String.format(
                "DR status=%s; time=%d; providerResult=%s",
                status != null ? status.name() : "UNKNOWN",
                time > 0 ? time : System.currentTimeMillis(),
                providerResult != null ? providerResult : ""
        );
    }
}
