package com.telemessage.simulators.web.wrappers;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Getter
@Slf4j
public abstract class AbstractMessage {

    @Schema(description = "Message source device", example = "17810000001")
    String src;
    @Schema(description = "Message destination device", example = "17810000002")
    String dst;

    @Schema(description = "Message text to send", example = "my text to be sent")
    protected abstract String getText();
}
