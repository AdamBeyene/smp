package com.telemessage.simulators.web.wrappers;

import com.telemessage.simulators.http.HttpConnection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Setter
@Getter
@Slf4j
public class HttpWebConnection extends WebConnection {

    boolean started;
    String url;
    String inUrl;
    String drUrl;

    public HttpWebConnection() {
    }

    public HttpWebConnection(HttpConnection conn) {
        super(conn.getId(), conn.getName());
        this.url = (!StringUtils.isEmpty(conn.getHttpMethod()) ? conn.getHttpMethod() + ": " : "")
            + "http://<simulator server:" + System.getProperty("com.telemessage.simulators.web.port") + ">/sim/http/connection/" + this.id + "/" + conn.getHttpMethod();
        this.drUrl = conn.getDrURL();
        this.inUrl = conn.getInUrl();
        this.started = conn.isStarted();
    }

}
