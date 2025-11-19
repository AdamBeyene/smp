package com.telemessage.simulators.http.gcm;

public final class Constants {
    public static final String GCM_SEND_ENDPOINT = "https://android.googleapis.com/gcm/send";
    public static final String PARAM_REGISTRATION_ID = "registration_id";
    public static final String PARAM_COLLAPSE_KEY = "collapse_key";
    public static final String PARAM_DELAY_WHILE_IDLE = "delay_while_idle";
    public static final String PARAM_PAYLOAD_PREFIX = "data.";
    public static final String PARAM_TIME_TO_LIVE = "time_to_live";
    public static final String ERROR_QUOTA_EXCEEDED = "QuotaExceeded";
    public static final String ERROR_DEVICE_QUOTA_EXCEEDED = "DeviceQuotaExceeded";
    public static final String ERROR_MISSING_REGISTRATION = "MissingRegistration";
    public static final String ERROR_INVALID_REGISTRATION = "InvalidRegistration";
    public static final String ERROR_MISMATCH_SENDER_ID = "MismatchSenderId";
    public static final String ERROR_NOT_REGISTERED = "NotRegistered";
    public static final String ERROR_MESSAGE_TOO_BIG = "MessageTooBig";
    public static final String ERROR_MISSING_COLLAPSE_KEY = "MissingCollapseKey";
    public static final String ERROR_UNAVAILABLE = "Unavailable";
    public static final String ERROR_INTERNAL_SERVER_ERROR = "InternalServerError";
    public static final String ERROR_INVALID_TTL = "InvalidTtl";
    public static final String TOKEN_MESSAGE_ID = "id";
    public static final String TOKEN_CANONICAL_REG_ID = "registration_id";
    public static final String TOKEN_ERROR = "Error";
    public static final String JSON_REGISTRATION_IDS = "registration_ids";
    public static final String JSON_PAYLOAD = "data";
    public static final String JSON_SUCCESS = "success";
    public static final String JSON_FAILURE = "failure";
    public static final String JSON_CANONICAL_IDS = "canonical_ids";
    public static final String JSON_MULTICAST_ID = "multicast_id";
    public static final String JSON_RESULTS = "results";
    public static final String JSON_ERROR = "error";
    public static final String JSON_MESSAGE_ID = "message_id";

    private Constants() {
        throw new UnsupportedOperationException();
    }
}
