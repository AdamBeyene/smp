package com.telemessage.simulators.http;

import com.telemessage.simulators.TM_QA_SMPP_SIMULATOR_Application;
import com.telemessage.simulators.common.RedisUtils;
import com.telemessage.simulators.controllers.message.MessageUtils;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.simulators.web.wrappers.HttpParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class HttpConnectionHandler<T> {

    protected HttpConnection connection;
    protected ThreadPoolExecutor executor = null;

    public abstract T generateDirectResponse(String postData, String providerResult);
    public abstract String generateDeliveryReceipt(String providerResult, String dr, HttpParam...params) throws UnsupportedEncodingException;
    public String sendDeliveryReceipt(String providerResult, String dr, HttpParam...params) throws UnsupportedEncodingException {
        String result = StringUtils.defaultString(generateDeliveryReceipt(providerResult, dr, params));
//        if (!StringUtils.isEmpty(providerResult) && result != null)
//            RedisUtils.addDataToRedis(providerResult, "dr", result);
        return result;
    }
    public abstract boolean generateIncoming(String src, String dst, String text, HttpParam...params);

    public void setHttpConnection(HttpConnection conn) {
        this.connection = conn;
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>(TM_QA_SMPP_SIMULATOR_Application.QUEUE_SIZE);
        executor = new ThreadPoolExecutor(conn.getThreads(), conn.getThreads(), 0L, TimeUnit.MILLISECONDS, queue);
    }

    public void waitFor(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException ignore) {}
    }
}
