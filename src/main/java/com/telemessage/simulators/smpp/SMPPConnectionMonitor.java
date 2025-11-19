package com.telemessage.simulators.smpp;

import com.logica.smpp.pdu.EnquireLink;
import com.logica.smpp.pdu.EnquireLinkResp;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class SMPPConnectionMonitor implements Runnable {

    static final int DEF_ENQUIRE_LINK_SESSION_LOCK_TIME = 60000;
    static final int MIN_ENQUIRE_LINK_SESSION_LOCK_TIME = 1000;
    final Object stateLock = new Object();

    protected SMPPConnManager connManager;
    @Setter
    @Getter
    protected int enquireLinkSessionLockTime = DEF_ENQUIRE_LINK_SESSION_LOCK_TIME;
    @Setter
    @Getter
    protected long lastMessage;
    @Setter
    @Getter
    protected long lastAckedEnquireLinkTime;
    @Setter
    protected DispatcherMonitorListener listener;
    protected State state;
    protected String name;


    private enum State{
        PLAY, PAUSE, STOP //Once state is set to STOP, monitor is treated as "dead".
    }

    public SMPPConnectionMonitor(String name, SMPPConnManager connManager, int enquireLinkSessionLockTime, long lastMessage, DispatcherMonitorListener listener) {
        super();
        if(enquireLinkSessionLockTime < MIN_ENQUIRE_LINK_SESSION_LOCK_TIME){
            throw new IllegalStateException("enquireLinkSessionLockTime minimal value is " + MIN_ENQUIRE_LINK_SESSION_LOCK_TIME);
        }
        this.connManager = connManager;
        this.listener = listener;
        this.state = State.PAUSE;
        this.enquireLinkSessionLockTime = enquireLinkSessionLockTime;
        this.lastMessage = lastMessage;
        this.name = name;
    }

    public String toString() {
        String host = connManager == null ? "N/A" : StringUtils.defaultString(connManager.getHost());
        String port = connManager == null ? "N/A" : String.valueOf(connManager.getPort());
        return (new StringBuilder()).append(StringUtils.defaultString(name)).append(" - host: ").append(host).append(":").append(port).append(" State: ").append(state).toString();
    }

    @Override
    public void run() {
        try {
            while (state != State.STOP) {
                long enquireLinkSessionLockTime = this.enquireLinkSessionLockTime;
                if (state != State.PAUSE) { // if we out of enquireLink wait, and during this wait pause was changed to false, let's not send enquire link
                    long enquireLinkRequestTime = System.currentTimeMillis();
                    try {
                        if (this.lastMessage <= 0 || enquireLinkRequestTime - this.lastMessage >= this.enquireLinkSessionLockTime) {
                            enquire();
                        } else {
                            enquireLinkSessionLockTime = this.enquireLinkSessionLockTime - (enquireLinkRequestTime - this.lastMessage);
                        }
                    } catch (Exception e) {
                        onFailure();
                    }
                }
                enquireLinkSessionLockTime = Math.max(enquireLinkSessionLockTime, MIN_ENQUIRE_LINK_SESSION_LOCK_TIME);
                synchronized (stateLock) {//if someone is updating the state, the main thread waits.
                    try {
                        if (state == State.PAUSE) {
                            stateLock.wait();//waits until notified.
                        } else if (state == State.PLAY) {
                            stateLock.wait(enquireLinkSessionLockTime);//waits until notified or up to enquireLinkSessionLockTime ms
                        }
                    } catch (InterruptedException e) {
                    }
                }
            }
        } finally {
        }
    }

    private void onFailure() {
        synchronized(stateLock){
            if (state == State.PLAY) {//Only if you're playing should you update your listener.
                state = State.PAUSE;
                try {
                    listener.onMonitorFailure();
                } catch (Exception exp) {
                }
            }
        }
    }

    private void enquire() throws Exception {
        EnquireLinkResp resp = (EnquireLinkResp)connManager.send(new EnquireLink());
        if (resp == null)
            throw new RuntimeException("ENQUIRE_LINK_RESP is null");
        else
            this.lastAckedEnquireLinkTime = System.currentTimeMillis();
    }

    private void checkState() throws IllegalStateException {
        if (state == State.STOP)
            throw new IllegalStateException("Monitor is stopped");
    }

    public void wakeup() {//Wakeup may (unexpected scenario) "wake" a monitor in his "PLAY wait". Not expected to cause any trouble.
        synchronized (stateLock) {
            checkState();
            if (state == State.PAUSE) {
                state = State.PLAY;
                stateLock.notify();
            }
        }
    }

    public void pause() {
        synchronized (stateLock) {
            checkState();
            state = State.PAUSE;
        }
    }

    public void shutDownMonitor() {
        synchronized (stateLock) {
            checkState();
            state = State.STOP;
            stateLock.notify();
        }
    }
}
