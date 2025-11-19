package com.telemessage.simulators.http.mirs;

import com.telemessage.simulators.web.wrappers.HttpParam;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;

@Slf4j
public class HttpConnectionHandler extends com.telemessage.simulators.http.HttpConnectionHandler<String> {

    @Override
    public String generateDirectResponse(String postData, String providerResult) {
        return this.connection.getDirectStatus() + "," + providerResult;
    }

    @Override
    public String generateDeliveryReceipt(String providerResult, String dr, HttpParam... params) throws UnsupportedEncodingException {
        return null;
    }

    @Override
    public boolean generateIncoming(String src, String dst, String text, HttpParam... params) {
        return false;
    }
}
