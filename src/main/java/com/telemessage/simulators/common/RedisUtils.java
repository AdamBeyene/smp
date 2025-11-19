package com.telemessage.simulators.common;

import com.lambdaworks.redis.ClientOptions;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.RedisURI;
import org.apache.commons.lang3.StringUtils;

public class RedisUtils {

    public static final String KEY_ENV_DELIMITER = ":";
    public static final String KEY_FEATURE_DELIMITER = "#";

    private final static int DEF_PORT = 6379;
    private static final RedisClient client;
    private static RedisConnection<String, String> conn = null;
    private static boolean enabled;
    static {

        try {
            enabled = Boolean.parseBoolean(StringUtils.defaultString(System.getProperty("redis.enabled"), "false"));
        } catch (Exception e) {
            enabled = false;
        }
        if (enabled) {
            RedisURI uri = RedisURI.Builder.redis(System.getProperty("redis.host"))
                    .withPort(DEF_PORT)
                    .withPassword(System.getProperty("redis.auth"))
                    .build();
            RedisClient cl = RedisClient.create(uri);
            cl.setOptions(new ClientOptions.Builder().autoReconnect(true).pingBeforeActivateConnection(true).build());
            client = cl;
            conn = cl.connect();
        } else {
            client = null;
            conn = null;
        }
    }

    public static RedisClient getClient() {
        return client;
    }

    public static void addDataToRedis(String msgId, String field, String value) {
        if (enabled) {
            try {
                conn.hset("simulator" + KEY_ENV_DELIMITER + msgId, field, value);
                conn.expire("simulator" + KEY_ENV_DELIMITER + msgId, Math.round(60 * 60 * 24 * 0.7)); // 16 hours
            } catch (Exception ignore) {}
        }
    }


    public static void silentClose(RedisConnection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignore){}
        }
    }
}
