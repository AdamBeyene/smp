package com.telemessage.simulators.web.wrappers;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.MutablePair;

@Slf4j
public class HttpParam extends MutablePair<String, String> {

    public HttpParam() {
    }

    public HttpParam(String left, String right) {
        super(left, right);
    }
}
