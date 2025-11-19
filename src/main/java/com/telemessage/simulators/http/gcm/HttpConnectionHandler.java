package com.telemessage.simulators.http.gcm;

import com.telemessage.simulators.common.RedisUtils;
import com.telemessage.simulators.web.wrappers.HttpParam;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.UnsupportedEncodingException;
import java.util.List;

public class HttpConnectionHandler extends com.telemessage.simulators.http.HttpConnectionHandler<GCMResult> {
    @Override
    public GCMResult generateDirectResponse(String postData, String providerResult) {
        boolean success = "success".equals(this.connection.getDirectStatus());
        GCMResult result = null;
        if (!StringUtils.isEmpty(postData)) {
            JSONParser parser = new JSONParser();
            try {
                JSONObject jsonObject = (JSONObject) parser.parse(postData);
                String to = (String)jsonObject.get("to");
                if (StringUtils.isEmpty(to)) {
                    List<String> ids = (JSONArray)jsonObject.get("registration_ids");
                    result = success ? new MulticastResult(providerResult, "", ids) :
                            new MulticastResult("", this.connection.getDirectStatus(), ids);
                } else {
                    result = success ? new SingleResult(providerResult, null) :
                            new SingleResult(this.connection.getDirectStatus());
                }
            } catch (Exception e) {
                result = null;
            }
        }
        if (result == null)
            result = new SingleResult("Simulator Error - check simulator");
//        RedisUtils.addDataToRedis(providerResult, "sent", String.valueOf(result));
        return result;
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
