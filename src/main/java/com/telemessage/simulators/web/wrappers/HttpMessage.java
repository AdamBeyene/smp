package com.telemessage.simulators.web.wrappers;


import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.media.Schema;

@Setter
@Slf4j
public class HttpMessage extends AbstractMessage {

    String text;
    HttpParam[] params;

    @Override
    public String getText() {
        return text != null ? text : "";
    }

    public void setText(String text) {
        this.text = text;
    }

    public HttpParam[] getParams() {
        return params;
    }

    public void setParams(HttpParam[] params) {
        this.params = params;
    }
}
