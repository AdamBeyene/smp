package com.telemessage.simulators.http.gcm;

import com.telemessage.simulators.common.Utils;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;

public class MulticastResult implements Serializable, GCMResult {
    @Setter
    @Getter
    protected int success;
    @Setter
    @Getter
    protected int failure;
    @Setter
    @Getter
    protected String multicastId;
    protected String errorCode;
    @Setter
    @Getter
    protected List<String> results;

    public MulticastResult(String multicastId, String errorCode, List<String> tokens) {
        this.results = tokens;
        this.errorCode = errorCode;
        this.multicastId = multicastId;
        this.failure = tokens.size();
        this.success = StringUtils.isEmpty(multicastId) ? 0 : tokens.size();
        this.failure = StringUtils.isEmpty(multicastId) ? tokens.size() : 0;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer("");
        int i=0;
        sb.append("{ \"multicast_id\": ").append(multicastId).append(",")
            .append("\"success\":").append(success).append(",")
            .append("\"failure\":").append(failure).append(",")
            .append("\"canonical_ids\":0,");
        sb.append("\"results\": [");
        for (String token : results) {
            if (i != 0)
                sb.append(",");
            if (success == 0)
                sb.append("{\"error\":\"").append(errorCode).append("\"}");
            else
                sb.append("{\"message_id\":\"1:").append(Utils.generateRandomKey(4, false)).append("\"}");
            i++;
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }
}
