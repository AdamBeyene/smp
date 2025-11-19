package com.telemessage.simulators.smpp;

import com.logica.smpp.Receiver;
import com.logica.smpp.Session;
import com.logica.smpp.TCPIPConnection;
import lombok.extern.slf4j.Slf4j;

import java.net.Socket;

@Slf4j
public class CloseSessionThread implements Runnable {

    protected final Session closeSession;

    public CloseSessionThread(Session closeSession) {
        this.closeSession = closeSession;
    }

    @Override
    public void run() {
        if (closeSession == null) {
            return;
        }
        try {
            // trying to close any inner data Logica has inside its implementation
            closeSession.close();
            Receiver r = closeSession.getReceiver();
            if (r != null) {
                r.stop();
            }
        } catch (Exception ignore) {}
        // we don't want to close gracefully, so we shutdown streams by ouselves
        if (closeSession.getConnection() != null) {
            TCPIPConnection c = (TCPIPConnection) closeSession.getConnection();
            Socket s = c.getSocket();
            if (s != null) {
                try {
                    s.shutdownOutput();
                } catch (Exception ignore) {
                }
                try {
                    s.shutdownInput();
                } catch (Exception ignore) {
                }
            }
        }
        closeSession.setReceiver(null);
        closeSession.setTransmitter(null);

    }
}
