package com.telemessage.simulators.http.cellcom;

import com.telemessage.simulators.http.HttpSimulator;
import com.telemessage.simulators.http.HttpUtils;
import com.telemessage.simulators.web.wrappers.HttpParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component("cellcomHttpConnectionImmediateHandler")
public class HttpConnectionImmediateHandler extends com.telemessage.simulators.http.HttpConnectionHandler<String> {

    private HttpSimulator HttpSim;

    @Autowired
    public HttpConnectionImmediateHandler(HttpSimulator HttpSim) {
        this.HttpSim = HttpSim;
    }

    private static final String DIRECT_RESPONSE_URL = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<ArrayOfSendSmsAck xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://tempuri.org/\">\n" +
            "  <SendSmsAck>\n" +
            "    <Success>${success}</Success>\n" +
            "    <MessageId>${providerResult}</MessageId>\n" +
            "    <MessageIdInt>${providerResultInt}</MessageIdInt>\n" +
            "    <ErrorCode>${status}</ErrorCode>\n" +
            "  </SendSmsAck>\n" +
            "</ArrayOfSendSmsAck>";

    @Override
    public String generateDirectResponse(String postData, String providerResult) {
        String success = "0".equals(this.connection.getDirectStatus()) ? "true" : "false";
        return DIRECT_RESPONSE_URL.replace("${success}", success).replace("${status}", this.connection.getDirectStatus()).replace("${providerResult}", StringUtils.defaultString(providerResult))
                .replace("${providerResultInt}", StringUtils.defaultString(providerResult));
    }

    private void sendMessage(final long time, final Header ipFrom, final String url, Map<String, String> httpParams) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpSim.getHttpUtils().sendGetMessage(time, ipFrom, url, "cellcom", httpParams);
            }
        });
    }

    private void logExecutorData() {
        log.info("Executor state - Shutdown: " + executor.isShutdown() + ", Terminated: " + executor.isTerminated() + ", Terminating" + executor.isTerminating());
        log.info("Executor state - Active Count: " + executor.getActiveCount() + ", Pool Size: " +
                executor.getPoolSize() + ", Completed Tasks: " + executor.getCompletedTaskCount() + ", Task Count: " +
                executor.getTaskCount() + ", Queue size: " + executor.getQueue().size());
    }

    @Override
    public String generateDeliveryReceipt(String providerResult, String dr, HttpParam... params) throws UnsupportedEncodingException {
        Map<String, String> httpParams = new HashMap<>();
        String url = this.connection.getDrURL();
       // url = url + (url.contains("?") ? (url.endsWith("&") ? "" : "&") : "?");
        url=url;
        //url =  url + "receiptedMessageId=" + providerResult + "&messageState=" + dr;
        httpParams.put("receiptedMessageId", providerResult);
        httpParams.put("messageState", dr);
        if (params != null) {
            for (Pair<String, String> p : params) {
                //url = url + "&" + p.getLeft() + "=" + URLEncoder.encode(p.getRight(), "UTF-8");
                httpParams.put(p.getLeft(), p.getRight());
            }
        }
        Header ipFrom = !StringUtils.isEmpty(this.connection.getDrFromIP()) ? new BasicHeader("X-Forwarded-For", this.connection.getDrFromIP()) : null;
        log.info("Sending DR to " + url);
        logExecutorData();
        sendMessage(0L, ipFrom, url,httpParams);
        return url;
    }

    @Override
    public boolean generateIncoming(String src, String dst, String text, HttpParam... params) {
        Map<String, String> httpParams= new HashMap<>();
        String url = this.connection.getInUrl();
        if (url == null)
            throw new IllegalArgumentException("Incoming is not defined for this connection");

      //  url = url + (url.contains("?") ? (url.endsWith("&") ? "" : "&") : "?");
        url=url;
        // url = url + "source=" + src + "&target=" + dst + "&text=" + URLEncoder.encode(text, "UTF-8");
        httpParams.put("source", src);
        httpParams.put("target", dst);
        httpParams.put("text", text);
        if (params != null) {
            for (Pair<String, String> p : params) {
               // url = url + "&" + p.getLeft() + "=" + URLEncoder.encode(p.getRight(), "UTF-8");
                httpParams.put(p.getLeft(), p.getRight());
            }
        }
        log.info("Sending message to " + url);
        logExecutorData();
        sendMessage(0, null, url,httpParams);
        return true;
    }
}
