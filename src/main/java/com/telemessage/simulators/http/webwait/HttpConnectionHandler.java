package com.telemessage.simulators.http.webwait;

import com.telemessage.simulators.web.wrappers.HttpParam;

import java.io.UnsupportedEncodingException;

public class HttpConnectionHandler extends com.telemessage.simulators.http.HttpConnectionHandler<String> {
    private final static String RESPONSE = "OK";

    @Override
    public String generateDirectResponse(String postData, String providerResult) {
        boolean success = "success".equals(this.connection.getDirectStatus());
        return "";
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
