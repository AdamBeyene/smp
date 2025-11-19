package com.telemessage.simulators.smpp;

import com.logica.smpp.pdu.Request;
import com.logica.smpp.pdu.Response;

import java.io.IOException;
import java.util.EventListener;

public interface SMPPConnManagerListener extends EventListener {
    void onStart(boolean success);
    void onClose(boolean success);
    void handleRequest(Request request, SMPPRequestManager requestManager) throws IOException;
    void handleResponse(Response response, SMPPRequestManager requestManager);
}