package com.telemessage.simulators.http;

import com.telemessage.simulators.conf.AbstractConnection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.springframework.context.ApplicationContext;

@Slf4j
public class HttpConnection extends AbstractConnection {

    @Setter
    @Getter
    @Element (name = "dr_url", required = false) String drURL;
    @Setter
    @Getter
    @Element (name = "dr_from_ip", required = false) String drFromIP;
    @Setter
    @Getter
    @Element (name = "in_url", required = false) String inUrl;
    @Setter
    @Getter
    @Attribute protected String impl;
    @Setter
    @Getter
    @Attribute(required = false, name = "method") String httpMethod;
    @Setter
    @Getter
    @Element (name = "threads", required = false) int threads = 1;
    @Setter
    @Getter
    @Element (name = "queue", required = false) int queue;
    @Setter
    @Getter
    boolean started;

    public void start() { this.started = true; }
    public void stop() { this.started = false; }

    private HttpConnectionHandler handler = null;
    public final Object lock = new Object();
    private ApplicationContext applicationContext;

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public HttpConnectionHandler getConnectionHandler() {
        if (StringUtils.isEmpty(impl))
            return null;
        if (handler == null) {
            synchronized (lock) {
                if (handler == null) {
                    try {
                        if (applicationContext != null) {
                            // Try to get bean from Spring context first
                            try {
                                handler = (HttpConnectionHandler) applicationContext.getBean(Class.forName(impl));
                            } catch (Exception e) {
                                // Fallback to manual instantiation for non-Spring managed classes
                                handler = (HttpConnectionHandler)Class.forName(impl).newInstance();
                            }
                        } else {
                            handler = (HttpConnectionHandler)Class.forName(impl).newInstance();
                        }
                        handler.setHttpConnection(this);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return handler;
    }
}
