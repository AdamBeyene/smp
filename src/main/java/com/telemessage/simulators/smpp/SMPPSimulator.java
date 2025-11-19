package com.telemessage.simulators.smpp;


import com.logica.smpp.pdu.*;
import com.telemessage.simulators.Simulator;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.common.services.filemanager.SimFileManager;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import com.telemessage.simulators.smpp.conf.SMPPConnections;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.simpleframework.xml.core.Persister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class SMPPSimulator extends Thread implements Simulator {

    @Getter
    private final MessagesCache messagesCacheService;
    EnvConfiguration conf;

    @Getter
    SMPPConnections conns;

    @Autowired
    public SMPPSimulator(EnvConfiguration conf, MessagesCache messagesCache) {
        this.conf = conf;
        state = State.starting;
        this.messagesCacheService = messagesCache;
    }

    @PostConstruct
    public void init() {
        try {
            readFromConfiguration();
            System.out.println();
        } catch (Exception e) {
            log.error("Failed to initialize from configuration", e);
        }
    }

    static {
        // initialize a PDU to prevent a bug in logica's code (See JLS 12.4.2)
        PDU.createPDU(0);
    }

    public static final String CONN_FILE = "smpps.xml";

    public enum State {
        started, shutdown, starting, invalid;
    }

    private Map<Integer, SMPPConnectionConf> connectionMap = new ConcurrentHashMap<>();
    private State state;

    private void disconnectAll() {
        for (SMPPConnectionConf c : connectionMap.values()) {
            for (SMPPConnection s : c.getAllConnections()) {
                if (s != null)
                    s.disconnect();
            }
        }
    }

    public void startConnections() {
        state = State.started;
        try {
            for (SMPPConnectionConf c : connectionMap.values()) {
                for (SMPPConnection s : c.getAllConnections()) {
                    if (s != null && !s.isBound()) {
                        log.info("Starting connection: {}", s.toString());
                        s.start();
                    }
                }
            }
        } catch (Exception e) {
            state = State.invalid;
            log.error("Error starting connections, shutting down all", e);
            disconnectAll();
            connectionMap.clear();
        }
    }

    public void shutdown() {
        state = State.shutdown;
        log.info("Shutting down all SMPP connections");
        disconnectAll();
        connectionMap.clear();
    }

    public State getManagerState() {
        return state;
    }

    public SMPPConnectionConf get(int id) {
        return this.connectionMap.get(id);
    }

    public SMPPReceiver getReceiver(int id) {
        SMPPConnectionConf conn = this.connectionMap.get(id);
        if (conn == null)
            return null;
        return conn.getReceiver();
    }

    public SMPPTransmitter getTransmitter(int id) {
        SMPPConnectionConf conn = this.connectionMap.get(id);
        if (conn == null)
            return null;
        return conn.getTransmitter();
    }

    public int getTransmitterRef(int id) {
        SMPPConnectionConf conn = this.connectionMap.get(id);
        if (conn == null) {
            log.debug("conn = null return 0");
            return 0;
        }
        log.debug("conn.getTransmitterRef() success");
        return conn.getTransmitterRef();
    }

    public SMPPTransceiver getTransceiver(int id) {
        SMPPConnectionConf conn = this.connectionMap.get(id);
        if (conn == null) {
            log.debug("conn = null return null");
            return null;
        }
        log.debug("conn.getTransmitterRef() success");
        return conn.getTransceiver();
    }

    public void removeReceiver(int id) {
        SMPPConnectionConf conn = this.connectionMap.get(id);
        if (conn == null) return;
        if (conn.getReceiver() == null) return;
        try {
            conn.getReceiver().disconnect();
        } catch (Exception any) {
            log.debug("Failed to stop connection");
            throw new RuntimeException(any);
        }
        conn.setReceiver(null);
    }

    public void removeTransmitter(int id) {
        SMPPConnectionConf conn = this.connectionMap.get(id);
        if (conn == null) return;
        if (conn.getTransmitter() == null) return;
        try {
            conn.getTransmitter().disconnect();
        } catch (Exception any) {
            log.debug("Failed to stop connection");
            throw new RuntimeException(any);
        }
        conn.setTransmitter(null);
    }

    public void remove(int id) {
        removeReceiver(id);
        removeTransmitter(id);
        this.connectionMap.remove(id);
    }

    public boolean update(SMPPConnectionConf conn) {
        boolean success = false;
        if (conn.getTransmitterRef() > 0) {
            SMPPTransmitter tr = this.getTransmitter(conn.getTransmitterRef());
            if (tr != null) {
                conn.setTransmitter(tr);
                success = true;
            } else {
                conn.setTransmitter(null);
            }
        } else {
            this.initConnection(conn, this.connectionMap, true);
            success = true;
        }
        return success;
    }

    public void initConnection(SMPPConnectionConf c, Map<Integer, SMPPConnectionConf> nextDispatchers, boolean start) {
        SMPPConnectionConf conn = this.connectionMap.get(c.getId());
        if (conn != null && conn.equals(c)) {
            nextDispatchers.put(conn.getId(), conn);
        } else {
            if (conn != null) {
                if (conn.getReceiver() != null) {
                    try {
                        conn.getReceiver().disconnect();
                    } catch (Exception any) {
                        log.debug("Failed to stop connection");
                        throw new RuntimeException(any);
                    }
                }
                if (conn.getTransmitter() != null) {
                    try {
                        conn.getTransmitter().disconnect();
                    } catch (Exception any) {
                        log.debug("Failed to stop connection");
                        throw new RuntimeException(any);
                    }
                }
            }
            nextDispatchers.put(c.getId(), c);
            if (start) {
                for (SMPPConnection s : c.getAllConnections()) {
                    if (s != null) {
                        s.start();
                    }
                }
            }
        }
    }

    public void readFromConfiguration() throws Exception {
        String env = conf.getEnvCurrent();
        log.info("Using environment {}", env);
        Path filename = Paths.get(StringUtils.isEmpty(env) ? "" : env).resolve(CONN_FILE);
        log.info("SMPP conf file path :{}", filename);
        InputStream inputStream = SimFileManager.getResolvedResourcePath(filename.toString());
        conns = new Persister().read(SMPPConnections.class, inputStream);
        log.debug("readConfiguration result is " + conns.toString());
        Map<Integer, SMPPConnectionConf> nextDispatchers = new ConcurrentHashMap<>();
        Map<Integer, Integer> refs = new ConcurrentHashMap<>();
        for (SMPPConnectionConf c : conns.getConnections()) {
            if (c != null) {
                if (c.getRef() > 0) {
                    refs.put(c.getId(), c.getRef());
                    c.setTransmitter(null);
                }
                this.initConnection(c, nextDispatchers, false);
            }
        }
        for (Integer id : refs.keySet()) {
            SMPPConnectionConf c = nextDispatchers.get(refs.get(id));
            if (c != null && c.getTransmitter() != null) {
                nextDispatchers.get(id).setTransmitter(new SMPPTransmitterReadonly(c.getTransmitter(), id));
            }

        }
        this.connectionMap = nextDispatchers;
    }

    public boolean send(int id, SMPPRequest req, boolean sendAllPartsOfConcatenateMessage) throws UnsupportedEncodingException, IntegerOutOfRangeException, WrongLengthOfStringException, WrongDateFormatException {
        SMPPTransmitter tr = null;
        SMPPTransceiver transceiver = null;
        log.debug("start send message");
        int transceiverref = getTransmitterRef(id);
        if (transceiverref > 0) {
            tr = getTransmitter(transceiverref);
            transceiver = getTransceiver(transceiverref);
            log.debug("getTransceiver(transceiverref) success, transceiverref: {}" , transceiverref);
        }

        if (tr == null) {
            tr = getTransmitter(id);
        }
        if (transceiver == null) {
            transceiver = getTransceiver(id);
        }

        if (tr != null) {
            List<SendMessageSM> msgs = tr.prepareMessage(req, sendAllPartsOfConcatenateMessage);
            log.debug("message prepare success");
            if (req.getPartsDelay() == null || req.getPartsDelay().isEmpty()) {
                for (SendMessageSM m : msgs) {
                    tr.send(m);
                    log.debug("message send success");
                }
            } else {
                SMPPTransmitter finalTr = tr;
                sendMessagesWithDelays(finalTr, msgs, req.getPartsDelay());
            }
            return true;
        } else if (transceiver != null) {
            List<SendMessageSM> msgs = transceiver.prepareMessage(req, sendAllPartsOfConcatenateMessage);
            log.debug("message prepare success");
            if (req.getPartsDelay() == null || req.getPartsDelay().isEmpty()) {
                for (SendMessageSM m : msgs) {
                    transceiver.send(m);
                    log.debug("message send success");
                }
            } else {
                SMPPTransceiver finalTr = transceiver;
                sendMessagesWithDelays(finalTr, msgs, req.getPartsDelay());
            }
            return true;
        }
        return false;
    }

    private void sendMessagesWithDelays(SMPPTransmitter tr, List<SendMessageSM> msgs, List<Long> partsDelay) {
        try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {

            int totalParts = msgs.size();
            int delaySize = partsDelay.size();
            List<Long> actualPartsDelay = new ArrayList<>(totalParts);

            for (int i = 0; i < totalParts; i++) {
                if (i < totalParts - delaySize) {
                    actualPartsDelay.add(0L);
                } else {
                    actualPartsDelay.add(partsDelay.get(i - (totalParts - delaySize)));
                }
            }

            int msgPartDelay = 0;
            for (int i = 0; i < totalParts; i++) {
                long delay = actualPartsDelay.get(i);
                msgPartDelay += delay;

                int finalIndex = i;
                if (delay == 0) {
                    tr.send(msgs.get(i));
                } else {
                    scheduler.schedule(() -> {
                        try {
                            tr.send(msgs.get(finalIndex));
                            log.info("Message part delay: part {} sent successfully with delay of {} ms", finalIndex + 1, delay);
                        } catch (Exception e) {
                            log.error("Failed to send message part: {}", finalIndex + 1, e);
                        }
                    }, msgPartDelay, TimeUnit.MILLISECONDS);
                }
            }
            scheduler.shutdown();
            log.info("Message part delay finish: All message parts sent.  last part delay was {}, total parts {}", msgPartDelay, totalParts);
        }
    }

    private void sendMessagesWithDelays(SMPPTransceiver tr, List<SendMessageSM> msgs, List<Long> partsDelay) {
        try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {

            int totalParts = msgs.size();
            int delaySize = partsDelay.size();
            List<Long> actualPartsDelay = new ArrayList<>(totalParts);

            for (int i = 0; i < totalParts; i++) {
                if (i < totalParts - delaySize) {
                    actualPartsDelay.add(0L);
                } else {
                    actualPartsDelay.add(partsDelay.get(i - (totalParts - delaySize)));
                }
            }

            int msgPartDelay = 0;
            for (int i = 0; i < totalParts; i++) {
                long delay = actualPartsDelay.get(i);
                msgPartDelay += delay;

                int finalIndex = i;
                if (delay == 0) {
                    tr.send(msgs.get(i));
                } else if (delay==999999999) {
                    log.debug("part will not be sent, delay is 999999999");
                } else {
                    scheduler.schedule(() -> {
                        try {
                            tr.send(msgs.get(finalIndex));
                            log.info("Message part delay: part {} sent successfully with delay of {} ms", finalIndex + 1, delay);
                        } catch (Exception e) {
                            log.error("Failed to send message part: {}", finalIndex + 1, e);
                        }
                    }, msgPartDelay, TimeUnit.MILLISECONDS);
                }
            }
            scheduler.shutdown();
            log.info("Message part delay finish: All message parts sent.  last part delay was {}, total parts {}", msgPartDelay, totalParts);
        }
    }

    public Map<Integer, SMPPConnectionConf> getConnections() {
        return this.connectionMap;
    }

    private final Object lockRun = new Object();

    @Override
    public void run() {
        while (state != State.shutdown) {
            if (state == State.starting)
                startConnections();
            synchronized (lockRun) {
                try {
                    lockRun.wait();
                } catch (InterruptedException ignore) {
                }
            }
        }
    }
}
