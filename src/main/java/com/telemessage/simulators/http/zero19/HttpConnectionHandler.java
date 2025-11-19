package com.telemessage.simulators.http.zero19;

import com.telemessage.simulators.http.HttpSimulator;
import com.telemessage.simulators.http.HttpUtils;
import com.telemessage.simulators.web.wrappers.HttpParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component("zero19HttpConnectionHandler")
public class HttpConnectionHandler extends com.telemessage.simulators.http.HttpConnectionHandler<String> {

    private HttpSimulator HttpSim;

    @Autowired
    public HttpConnectionHandler(HttpSimulator HttpSim) {
        this.HttpSim = HttpSim;
    }

    public enum Status {
        SUCCESS(0, "SMS will be sent"),
        XML_PARSER_ERROR(1, "There was a problem parsing your XML"),
        SOME_ERROR(2, "missing field"),
        INVALID_CREDENTIALS_ERROR(3, "Username or password is incorrect"),
        NOT_ENOUGH_CREDIT_ERROR(4, "Not enough credit"),
        NO_PERMISSIONS_ERROR(5, "No permission to send SMS at this time"),
        NOT_VALID_COMMAND_ERROR(997, "Not a valid command sent"),
        UKNOWN_ERROR(998, "There was an unknown error in the request"),
        CONTACT_SUPPORT_ERROR(999, "Contact support"),
        ;
        final int status;
        final String desc;

        Status(int status, String desc) {
            this.desc = desc;
            this.status = status;
        }

        public int getStatus() {
            return status;
        }

        public String getDesc() {
            return desc;
        }

        static final Map<String, Status> reverse = new ConcurrentHashMap<>();
        static {
            for (Status s : Status.values()) {
                reverse.put(String.valueOf(s.status), s);
            }
        }

        public static Status get(String status) {
            return reverse.get(status);
        }
    }

    private static final String DIRECT_RESPONSE_URL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<sms>\n" +
            "<status>${status}</status>\n" +
            "<message>${status_desc}</message>\n" +
            "<shipment_id>${providerResult}</shipment_id>\n" +
            "</sms>";

    @Override
    public String generateDirectResponse(String postData, String providerResult) {
        Status st = Status.get(this.connection.getDirectStatus());
        if (st == null)
            throw new IllegalArgumentException("Invalid status " + this.connection.getDirectStatus());
        return DIRECT_RESPONSE_URL.replace("${status}", String.valueOf(st.getStatus())).replace("${status_desc}", st.getDesc())
                .replace("${providerResult}", StringUtils.defaultString(providerResult));
    }

    private void sendMessage(final long time, final Header ipFrom, final String url,Map<String, String> httpParams) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpSim.getHttpUtils().sendGetMessage(time, ipFrom, url, "zero19", httpParams);
            }
        });
    }

    @Override
    public String generateDeliveryReceipt(String providerResult, String dr, HttpParam... params) throws UnsupportedEncodingException {
        Map<String, String> httpParams = new HashMap<>();
        String url = this.connection.getDrURL();
      //  url = url + (url.contains("?") ? (url.endsWith("&") ? "" : "&") : "?");
        url=url;
       // url =  url + "shipment_id=" + providerResult + "&status=" + dr;
        httpParams.put("shipment_id", providerResult);
        httpParams.put("status", dr);
        if (params != null) {
            for (Pair<String, String> p : params) {
                //url = url + "&" + p.getLeft() + "=" + URLEncoder.encode(p.getRight(), "UTF-8");
                httpParams.put(p.getLeft(), p.getRight());
            }
        }

        Header ipFrom = !StringUtils.isEmpty(this.connection.getDrFromIP()) ? new BasicHeader("X-Forwarded-For", this.connection.getDrFromIP()) : null;
        sendMessage(250L, ipFrom, url,httpParams);
        return url;
    }

    @Override
    public boolean generateIncoming(String src, String dst, String text, HttpParam... params) {
        Map<String, String> httpParams= new HashMap<>();
        String url = this.connection.getInUrl();
        if (url == null)
            throw new IllegalArgumentException("Incoming is not defined for this connection");

       // url = url + (url.contains("?") ? (url.endsWith("&") ? "" : "&") : "?");
        url=url;
        //url = url + "source=" + src + "&target=" + dst + "&text=" + URLEncoder.encode(text, "UTF-8");
        httpParams.put("source", src);
        httpParams.put("target", dst);
        httpParams.put("text", text);
        if (params != null) {
            for (Pair<String, String> p : params) {
               // url = url + "&" + p.getLeft() + "=" + URLEncoder.encode(p.getRight(), "UTF-8");
                httpParams.put(p.getLeft(), p.getRight());
            }
        }
        sendMessage(0L, null, url,httpParams);
        return true;
    }

}
