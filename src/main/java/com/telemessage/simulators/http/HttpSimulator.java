package com.telemessage.simulators.http;

import com.telemessage.simulators.Simulator;
import com.telemessage.simulators.common.Utils;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.common.services.filemanager.SimFileManager;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.simulators.http.conf.HttpConnections;
import com.telemessage.simulators.web.CustomNotFoundException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.simpleframework.xml.core.Persister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class HttpSimulator implements Simulator {

    private EnvConfiguration conf;
    @Getter
    private final MessagesCache messagesCacheService;
    @Getter
    HttpConnections conns;
    @Getter
    private HttpUtils httpUtils;
    private ApplicationContext applicationContext;

    @Autowired
    public HttpSimulator(EnvConfiguration conf, MessagesCache messagesCache, HttpUtils httpUtils, ApplicationContext applicationContext) {
        this.httpUtils = httpUtils;
        this.conf = conf;
        this.messagesCacheService = messagesCache;
        this.applicationContext = applicationContext;

        try {
            readFromConfiguration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static final String CONN_FILE = "https.xml";

    private static final Object lock = new Object();
    private static HttpSimulator instance = null;

/*    public static HttpSimulator get() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null)
                    instance = new HttpSimulator();
            }
        }
        return instance;
    }*/

    protected Map<Integer, HttpConnection> connectionMap = new ConcurrentHashMap<>();

    /*public HttpSimulator() {
        try {
            readFromConfiguration();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }*/

    public void readFromConfiguration() throws Exception {
        String env = conf.getEnvCurrent();
        log.info("Using environment {}", env);
        Path filename = Paths.get(StringUtils.isEmpty(env) ? "" : env).resolve(CONN_FILE);
        log.info("HTTP conf file path :{}", filename);
        InputStream inputStream = SimFileManager.getResolvedResourcePath(filename.toString());
        conns = new Persister().read(HttpConnections.class, inputStream);
        Map<Integer, HttpConnection> nextDispatchers = new ConcurrentHashMap<>();
//        for (HttpConnection c : Utils.deNull(conns.getConnections())) {
//            nextDispatchers.put(c.getId(), c);
//            c.start();
//        }
        for (HttpConnection c : Utils.deNull(conns.getConnections())) {
            c.setApplicationContext(applicationContext);
            nextDispatchers.put(c.getId(), c);
            c.start();
        }
        this.connectionMap = nextDispatchers;
    }

    public void start() {
        for (HttpConnection c : Utils.deNull(this.connectionMap.values()))
            c.start();
    }

    public void stop() {
        for (HttpConnection c : Utils.deNull(this.connectionMap.values()))
            c.stop();
    }

    public void shutdown() {
        this.stop();
    }

    public HttpConnection get(int id) {
        return this.connectionMap.get(id);
    }

    public void remove(int id) {
        HttpConnection ht = this.connectionMap.get(id);
        if (ht != null)
            ht.stop();
    }

    public Map<Integer, HttpConnection> getConnections() {
        return connectionMap;
    }

    public boolean send(int id, HttpRequest request) {
        HttpConnection conn = this.get(id);
        if (conn == null)
            throw new CustomNotFoundException();
        HttpConnectionHandler handler = conn.getConnectionHandler();
        if (handler == null)
            throw new CustomNotFoundException();

        return handler.generateIncoming(request.getSrc(), request.getDst(), request.getText(), request.getParams());
    }

    public boolean sendDr(int id, DeliveryReceiptHttpMessage request) throws UnsupportedEncodingException {
        HttpConnection conn = this.get(id);
        if (conn == null)
            throw new CustomNotFoundException();
        HttpConnectionHandler handler = conn.getConnectionHandler();
        if (handler == null)
            throw new CustomNotFoundException();

        return handler.sendDeliveryReceipt(request.getMsgId(), String.valueOf(request.getStatus()), request.getParams()) != null;
    }
}
