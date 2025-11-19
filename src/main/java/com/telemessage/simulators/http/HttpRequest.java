package com.telemessage.simulators.http;

import com.telemessage.simulators.web.wrappers.HttpParam;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Getter
@Slf4j
public class HttpRequest {

    protected String text;
    protected String src;
    protected String dst;
    protected HttpParam[] params;

    public HttpRequest() {
    }

    public HttpRequest(String src, String dst, String text, HttpParam[] params) {
        this.dst = dst;
        this.src = src;
        this.text = text;
        this.params = params;
    }


}
