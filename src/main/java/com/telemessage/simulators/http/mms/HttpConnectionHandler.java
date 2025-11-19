package com.telemessage.simulators.http.mms;

import com.telemessage.simulators.web.wrappers.HttpParam;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;

public class HttpConnectionHandler extends com.telemessage.simulators.http.HttpConnectionHandler<String> {

    public enum StatusCode {
        UNKNOWN(0),
        SUCCESS(1000),
        PARTIALSUCCESS(1100),
        CLIENTERROR(2000),
        OPERATIONRESTRICTED(2001),
        ADDRESSERROR(2002),
        ADDRESSNOTFOUND(2003),
        MULTIMEDIACONTENTREFUSED(2004),
        MESSAGEIDNOTFOUND(2005),
        LINKEDIDNOTFOUND(2006),
        MESSAGEFORMATCORRUPT(2007),
        APPLICATIONIDNOTFOUND(2008),
        REPLYAPPLICATIONIDNOTFOUND(2009),
        SERVERERROR(3000),
        NOTPOSSIBLE(3001),
        MESSAGEREJECTED(3002),
        MULTIPLEADDRESSESNOTSUPPORTED(3003),
        APPLICATIONADDRESSINGNOTSUPPORTED(3004),
        SERVERIOERROR(3005),
        GENERALSERVICEERROR(4000),
        IMPROPERIDENTIFICATION(4001),
        UNSUPPORTEDVERSION(4002),
        UNSUPPORTEDOPERATION(4003),
        VALIDATIONERROR(4004),
        SERVICEERROR(4005),
        SERVICEUNAVAILABLE(4006),
        SERVICEDENIED(4007),
        APPLICATIONDENIED(4008);

        int code;


        StatusCode(int code) {
            this.code = code;
        }
    }

    private final static String RESPONSE = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
            "<root>\n" +
            "\t<transactionID>${providerResult}</transactionID>\n" +
            "\t<status>\n" +
            "\t\t<statusCode>${status}</statusCode>\n" +
            "\t\t<statusText>${statusText}</statusText>\n" +
            "\t</status>\n" +
            "\t<jobID>1826936</jobID>\n" +
            "</root>";

    @Override
    public String generateDirectResponse(String postData, String providerResult) {
        StatusCode st = StatusCode.valueOf(this.connection.getDirectStatus());
        return RESPONSE.replace("${status}", String.valueOf(st.code)).replace("${statusText}", this.connection.getDirectStatus()).replace("${providerResult}", StringUtils.defaultString(providerResult));
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
