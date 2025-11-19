package com.telemessage.simulators.smpp;

import com.logica.smpp.*;
import com.logica.smpp.pdu.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;

@Slf4j
public class SMSCConnManager extends SMPPConnManager {

    private static final int NEW_CONNECTION_FAILURE_TIMEOUT = 2000;

    Connection servConnection;

    private void initializeConnection(int port, long timeout) throws IOException {
        servConnection = new com.logica.smpp.TCPIPConnection(port);
        servConnection.setReceiveTimeout(timeout);
        servConnection.open();
    }

    /**
     * The "one" listen attempt called from <code>run</code> method. The listening is atomicised to allow contoled
     * unbinding of the listening. The length of the single listen attempt is defined by <code>acceptTimeout</code>.
     */
    private Connection waitForNewTCPIPConnection() {

        try {
            return servConnection.accept(); // Thread awaits here for a new connection
        } catch (InterruptedIOException e) {
            // thrown when the timeout expires => it's ok, we just didn't
            // receive anything
        } catch (IOException e) {
            // accept can throw this from various reasons
            // and we don't want to continue then (?)
        }
        return null;
    }

    private void stopSession() {
        if (session != null || servConnection != null) {
            new Thread(new CloseSessionThread(session), toString()).start();
            try {
                if (servConnection != null) {
                    servConnection.close();
                }
            } catch (Exception ignore) {}
            servConnection = null;
            session = null;
        }
    }

    @Override
    public synchronized State startConnection(SMPPConnection smpp) throws AlreadyBoundException {
        try {
            if (state != State.shutdown) {
                boolean success = false;
                if (state == State.binding)
                    return state;
                synchronized (lockObject) {
                    if (state == State.bound)
                        throw new AlreadyBoundException("Already bound");
                    state = State.binding;
                    setDescription(smpp);
                    try {
                        do {
                            try {
                                success = false;
                                stopSession();
                                if (servConnection == null) {
                                    log.info("Initializing server connection on port {}", smpp.getPort());
                                    initializeConnection(smpp.getPort(), smpp.getTimeout());
                                }
                                Connection connection = waitForNewTCPIPConnection();
                                if (connection != null) {
                                    session = new Session(connection);
                                    if(session.getConnection()!=null){
                                        TCPIPConnection conn = (TCPIPConnection) session.getConnection();
                                        try{
                                            InetAddress remoteAddress = conn.getSocket().getInetAddress();
                                            String remoteHostName = remoteAddress.getHostName();
                                            log.info("Connection socket info:\n" +
                                                    "               -HostAddress:Port : " + remoteAddress.getHostAddress() + ":" +smpp.getPort() +"\n"+
                                                    "               -HostName : " + remoteHostName
                                            );
                                        } catch (Exception ignore){}

                                    }
                                    Transmitter tr = new Transmitter(connection);
                                    Receiver rc = new Receiver(tr, connection);
                                    session.setTransmitter(tr);
                                    session.setReceiver(rc);
                                    rc.start();
                                    bindAttempts++;
                                    PDU pdu = null;
                                    pdu = rc.receive(smpp.timeout);
                                    if (pdu != null && pdu.isRequest()) {
                                        if (pdu.getCommandId() == Data.BIND_RECEIVER || pdu.getCommandId() == Data.BIND_TRANSCEIVER || pdu.getCommandId() == Data.BIND_TRANSMITTER) {
                                            Response response = processBindRequest(smpp, (BindRequest) pdu);
                                            session.open();
                                            session.respond(response);
                                            if (response.getCommandStatus() == Data.ESME_ROK) {
                                                success = true;
                                                updateBind(smpp.getName());
                                                setDescription(smpp);
                                                log.info("Bind successful for port {}", smpp.getPort());
                                            }
                                            if (success) {
                                                log.info("Connection started successfully: " + this.toString());
                                            }
                                            Thread.sleep(1000L);
                                            processOnStartListeners(success);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Error for " + getName() + ", port " + port, e);
                                //In case another thread already got the connection bound during 'wait', the state will be State.bound and the loop will break.
                                lockObject.wait(NEW_CONNECTION_FAILURE_TIMEOUT);
                            }
                        } while (state == State.binding && !requestingStop);
                    } catch (Exception e) {
                        log.info("Error for " + getName() + ", port " + port);
                        log.error("", e);
                    }
                }
            } else {
                state = State.unbound;
            }
            return state;
        } finally {

        }
    }

    private Response processBindRequest(SMPPConnection smpp, BindRequest request) {
        int commandStatus = -1;
        BindResponse bindResponse = (BindResponse) request.getResponse();
        if (state == State.bound) {
            // already bound
            commandStatus = Data.ESME_RALYBND;
        } else {
            if (smpp.getSystemId().equalsIgnoreCase(request.getSystemId()) &&
                    smpp.getPassword().equalsIgnoreCase(request.getPassword())) {
                try {
                    bindResponse.setSystemId(smpp.getSystemId());
                    commandStatus = Data.ESME_ROK;
                } catch (WrongLengthOfStringException e) {
                }
            } else {
                commandStatus = Data.ESME_RINVSYSID; // we don't distinguish between inv. user and pass
            }
        }
        //we can accept only receiver or  transciever (we send messages and receive DLRs)
        if (request.getCommandId() == Data.BIND_RECEIVER) {
            bindResponse.setCommandId(Data.BIND_RECEIVER_RESP);
        } else if (request.getCommandId() == Data.BIND_TRANSCEIVER) {
            bindResponse.setCommandId(Data.BIND_TRANSCEIVER_RESP);
        } else if (request.getCommandId() == Data.BIND_TRANSMITTER_RESP) {
            bindResponse.setCommandId(Data.BIND_TRANSMITTER_RESP);
        }
        bindResponse.setCommandStatus(commandStatus);
        return bindResponse;
    }

    @Override
    public boolean closeConnection(boolean sendUnbind) {
        boolean success = false;
        try {
            if (state != State.unbound && servConnection != null && sendUnbind && session != null && session.isBound()) {
                sendUnbind();
            }
        } catch (Exception e) {
        }
        synchronized (lockObject) {
            try {
                try {
                    if (state == State.unbound) {
                        return false;
                    }
                    stopSession();
                    success = true;
                } catch (Exception ex) {
                } finally {
                    servConnection = null;
                }
                processOnCloseListeners(success);
            } finally {
                state = State.unbound;
            }
        }
        return success;
    }

    @Override
    public void shutDown() {
        requestingStop = true;
        synchronized (lockObject) {
            if (!state.isClosable()) {
                return;
            }
            try {
                state = State.unbinding;
                closeConnection(true);
            } finally {
                requestingStop = false;
            }
        }
    }

    @Override
    public void respond(Response response) throws IOException {
        if (session != null) {
            synchronized (lockObject) {
                try {
                    session.respond(response);
                } catch (ValueNotSetException | WrongSessionStateException e) {
                    throw new IOException(e);
                }
            }
        } else {
            throw new IOException("Session is null");
        }
    }

    @Override
    public Response send(PDU pdu) throws IOException {
        Integer id = null;
        if (session != null) {
            synchronized (lockObject) {
                pdu.assignSequenceNumber();
                id = pdu.getSequenceNumber();
                // we put this check inside if bind was changed by start or close connection
                if (state == State.unbound)
                    throw new IOException("Connection is not bound");
                requests.put(id, id);
                try {
                    session.getTransmitter().send(pdu);
                } catch (IOException e) {
                    state = State.unbound;
                    throw e;
                } catch (ValueNotSetException e) {
                    throw new IOException(e);
                }
            }
            try {
                requests.waitForResponse(id);
            } catch (InterruptedException e) {
            }
            return requests.remove(Response.class, id);
        }
        throw new IOException("Session is null");
    }

    public boolean sendUnbind() {
        boolean result = false;
        Unbind unbindReq = new Unbind();
        unbindReq.assignSequenceNumber();
        Integer id = unbindReq.getSequenceNumber();
        requests.put(id, id);
        if (session != null) {
            try {
                session.send(unbindReq, false); ///***///
            } catch (Exception e) {
                return false;
            }

            try {
                requests.waitForResponse(id);
            } catch (InterruptedException e) {
            }
            UnbindResp response = requests.remove(UnbindResp.class, id);
            if (response != null && response.getCommandStatus() == Data.ESME_ROK) {
                result = true;
            } else {
            }
        } else {
            result = true;
        }

        return result;
    }

    @Override
    public String getLogName() {
        return String.valueOf(port);
    }
}
