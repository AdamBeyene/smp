package com.telemessage.simulators.smpp;

import com.logica.smpp.ServerPDUEvent;
import com.logica.smpp.ServerPDUEventListener;
import com.logica.smpp.Session;
import com.logica.smpp.pdu.PDU;
import com.logica.smpp.pdu.Request;
import com.logica.smpp.pdu.Response;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Important!!!!! State changes must be always be inside synchronized block on lockObject
 */
@Slf4j
abstract public class SMPPConnManager implements ServerPDUEventListener {

    public static final long WAIT_BEFORE_REBIND = 10000;

    public enum State {
        initializing (false), binding (true), bound (true), unbinding(false), unbound(false), shutdown(false), stopped(false);

        boolean closable;

        private State(boolean closable) {
            this.closable = closable;
        }

        public boolean isClosable() {
            return closable;
        }
    }

    final Object lockObject = new Object();

    protected SMPPRequestManager requests = new SMPPRequestManager();
    @Getter
    protected String providerId;
    Session session;
    protected int bindAttempts = 0;
    protected ConcurrentLinkedQueue<SMPPConnManagerListener> listeners = new ConcurrentLinkedQueue<SMPPConnManagerListener>();
    @Setter
    @Getter
    protected String name;
    @Setter
    @Getter
    protected String host;
    @Setter
    @Getter
    protected int port;
    @Getter
    protected State state = State.initializing;
    protected boolean requestingStop = false;

    public void setProviderId(Integer providerId) { this.providerId = providerId != null ? String.valueOf(providerId) : null; }


    public boolean addListener(SMPPConnManagerListener listener) { return listener == null || listeners.offer(listener); }
    public boolean removeListener(SMPPConnManagerListener listener) { return listeners.remove(listener); }
    public void clearListeners() { this.listeners = new ConcurrentLinkedQueue<SMPPConnManagerListener>(); }

    protected void setDescription(SMPPConnection smpp) {
        name = smpp.getName();
        host = smpp.getHost();
        port = smpp.getPort();
    }

    protected void updateBind(String name) {
        session.getReceiver().setServerPDUEventListener(this);
        session.getReceiver().setName("Rec-" + name);
        state = State.bound;
        bindAttempts = 0;
        log.info("Bind successful for connection: {}", name);
    }

    protected void processOnStartListeners(boolean success) {
        for (SMPPConnManagerListener listener : listeners) {
            try {
                listener.onStart(success);
            } catch (Exception e) {
                log.error("Error in onStart listener", e);
            }
        }
    }

    protected void processOnCloseListeners(boolean success) {
        for (SMPPConnManagerListener listener : listeners) {
            try {
                listener.onClose(success);
            } catch (Exception e) {
                log.error("Error in onClose listener", e);
            }
        }
    }

    public void handleEvent(ServerPDUEvent event) {
        PDU pdu = event.getPDU();
        try {
            for (SMPPConnManagerListener listener : listeners) {
                if (pdu.canResponse()) {
                    listener.handleRequest((Request)pdu, requests);
                } else {
                    listener.handleResponse((Response) pdu, requests);
                }
            }
        } catch (Throwable t) {
            log.error("Error handling ServerPDUEvent", t);
        }
    }

    @Override
    public String toString() {
        return (new StringBuilder()).append(StringUtils.defaultString(name)).append(" - host: ").append(StringUtils.defaultString(host)).append(":").append(port).toString();
    }

    public boolean isBound() {
        return state == State.bound;
    }

    public abstract State startConnection(SMPPConnection smpp) throws AlreadyBoundException;
    public abstract boolean closeConnection(boolean sendUnbind);
    public abstract void respond(Response response) throws IOException;
    public abstract void shutDown();
    public abstract Response send(PDU pdu) throws IOException;
    public abstract boolean sendUnbind();

    public String getLogName() {
        if (StringUtils.isEmpty(host) && port <= 0)
            return null;
        return host + " - " + port;
    }
}
