package com.telemessage.simulators.http;

import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.message.MessageUtils;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class HttpUtils {

    private final EnvConfiguration conf;
    private final MessagesCache cacheService;

    @Autowired
    public HttpUtils(MessagesCache cacheService, EnvConfiguration conf) {
        this.conf = conf;
        this.cacheService = cacheService;
    }

    public void sendGetMessage(final long time, final Header ipFrom, final String url, final String provider, Map<String, String> httpParams) {
        Response r = sendGetMessageInternal(time, ipFrom, url, new HashMap<>(httpParams));
        if (r != null) {
            if (r.getStatusLine() != null) {
                log.info("HTTP message status code: {}", r.getStatusLine());
            } else {
                log.info("HTTP message Status line is null for {}", url);
            }
//            &target=0538556194&source=Admin
//                    &message=Capture+activation+code+%3A+i0l87pvq&pushUrl=&validity=1440&
//                    replace=false&immediate=false&isBinary=false&deliveryReceipt=true&maxSegments=0
//        httpParams.get("source");
//        httpParams.get("text");
//        httpParams.get("target");
//            Map<String, String> paramMap = Arrays.stream(url.split("&")).map(s -> s.split("=", 2)).collect(Collectors.toMap(a -> a[0].trim(), a -> a.length > 1 ? a[1].trim() : ""));

            String fullUrl = buildFullUrl(url, httpParams);;
            String text = null;
            String mid = null;
            try {
                mid = "http_" + new Date().getTime();
                text = httpParams.get("text");

                log.debug("HTTP cache message:\n" +
                                "from: {},to: {},\n" +
                                "text: {},\ntext: {},\nurl: {}, mid: {}",
                        httpParams.get("source"), httpParams.get("target"), httpParams.get("text"), text, fullUrl, mid);

                MessagesObject cacheMessage = MessagesObject.builder()
                        .dir("OUT_http")
                        .id(mid)
                        .from(httpParams.get("source"))
                        .to(httpParams.get("target"))
                        .text(text)
                        .httpMessage(fullUrl)
                        .directResponse(r.getStatusLine() + "\n" + r.asPrettyString())
                        .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                        .providerId(String.valueOf(provider))
                        .build();
                boolean ok = cacheService.addCacheRecord(mid, cacheMessage);
                if (!ok) log.error("Failed to add HTTP message to cache for id {}", mid);
            } catch (Exception e) {
                log.info("HTTP cache message:\n" +
                                "from: {},to: {},\n" +
                                "text: {},\ntext: {},\nurl: {}, mid: {}",
                        httpParams.get("source"), httpParams.get("target"), httpParams.get("message"), text, fullUrl, mid);
                log.error("Failed to add HTTP message to cache for url {}: {}", fullUrl, e.getMessage(), e);
            }
        } else {
            log.info("HTTP message Response is null for {}", url);
        }
    }

    public static String buildFullUrl(String baseUrl, Map<String, String> httpParams) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("Base URL cannot be null or empty");
        }
        if (httpParams == null || httpParams.isEmpty()) {
            return baseUrl;
        }

        String queryString = httpParams.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        return baseUrl + "?" + queryString;
    }

    public Response sendGetMessageInternal(final long time, final Header ipFrom, final String url, Map<String, String> httpParams) {
        try {
            log.info("Going to submit url {}", url);
            if (time > 0) {
                try {
                    Thread.sleep(250L);
                } catch (Exception ignore) {}
            }

            RequestSpecification request = RestAssured.given().queryParams(httpParams);

            if (ipFrom != null)
                request.header(ipFrom.getName(), ipFrom.getValue());

            if(log.isDebugEnabled()) {
                log.debug("HTTP request params: {}", httpParams);
                request.log().all();
            }

            ValidatableResponse req = request.get(url).then();

            if(log.isDebugEnabled()) {
                req.log().all();
            }

            Response r =req.extract().response();
            if (r != null) {
                if (r.getStatusLine() != null) {
                    log.info("HTTP response status: {}", r.getStatusLine());
                } else {
                    log.info("Status line is null for {}", url);
                }
            } else {
                log.info("Response is null for {}", url);
            }
            return r;
        } catch (Exception e) {
            log.error("HTTP request failed for url {}: {}", url, e.getMessage(), e);
        }
        return null;
    }

    public Map<String, String> getMapFromPostData(String postData) {
        Map<String, String> postDataMap = new HashMap<>();
        if (!StringUtils.isEmpty(postData)) {
            String[] data = postData.split("&");
            for (String datum : data) {
                String[] s = datum.split("=");
                String k = s[0];
                String v = "";
                try {
                    v = s.length > 1 && !StringUtils.isEmpty(s[1]) ? s[1] : "";
                } catch (Exception e) {
                    v = "";
                }
                if (!StringUtils.isEmpty(k)) {
                    postDataMap.put(k, v);
                }
            }
        }
        return postDataMap;
    }
}
