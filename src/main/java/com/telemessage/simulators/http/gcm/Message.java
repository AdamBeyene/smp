package com.telemessage.simulators.http.gcm;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;

@Setter
@Getter
public class Message implements Serializable {
    protected String collapseKey;
    protected Boolean delayWhileIdle;
    protected Integer timeToLive;
    protected Map<String, String> data;

    public Message() {
    }

    public String toString() {
        StringBuilder builder = new StringBuilder("Message(");
        if(this.collapseKey != null) {
            builder.append("collapseKey=").append(this.collapseKey).append(", ");
        }

        if(this.timeToLive != null) {
            builder.append("timeToLive=").append(this.timeToLive).append(", ");
        }

        if(this.delayWhileIdle != null) {
            builder.append("delayWhileIdle=").append(this.delayWhileIdle).append(", ");
        }

        if(!this.data.isEmpty()) {
            builder.append("data: {");
            Iterator i$ = this.data.entrySet().iterator();

            while(i$.hasNext()) {
                Map.Entry entry = (Map.Entry)i$.next();
                builder.append((String)entry.getKey()).append("=").append((String)entry.getValue()).append(",");
            }

            builder.delete(builder.length() - 1, builder.length());
            builder.append("}");
        }

        if(builder.charAt(builder.length() - 1) == 32) {
            builder.delete(builder.length() - 2, builder.length());
        }

        builder.append(")");
        return builder.toString();
    }
}
