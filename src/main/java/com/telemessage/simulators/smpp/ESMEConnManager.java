package com.telemessage.simulators.smpp;

import com.logica.smpp.*;
import com.logica.smpp.pdu.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class ESMEConnManager extends SMPPConnManager {
    public static final int MAX_BIND_ATTEMPTS_BEFORE_LONG_SLEEP = 30;
    public static final int MAX_BIND_ATTEMPTS_FACTOR = 10;

    private void shutDownSession() {
        if (session != null) {
            new Thread(new CloseSessionThread(session), toString() + " shutD").start();
            session = null;
        }
    }

    private void createSession(String ip, int port, long timeout) {
        Connection connection = new TCPIPConnection(ip, port);
        connection.setReceiveTimeout(timeout);
        connection.setCommsTimeout(timeout);
        session = new Session(connection);
    }

    @Override
    public State startConnection(SMPPConnection smpp) throws AlreadyBoundException {
        if (state == State.shutdown)
            return State.unbound;
        try {
            boolean success = false;
            if (state == State.binding)
                return state;
            synchronized (lockObject) {
                if (state == State.bound)
                    throw new AlreadyBoundException("Already bound or additional request");
                state = State.binding;
                setDescription(smpp);
                while (state == State.binding && !requestingStop) {
                    success = false;
                    try {
                        bindAttempts++;
                        log.debug("Bind attempt {} to {}:{} [systemId={}, bindOption={}]", bindAttempts, smpp.getHost(), smpp.getPort(), smpp.getSystemId(), smpp.getBindOption());
                        shutDownSession();
                        createSession(smpp.getHost(), smpp.getPort(), smpp.getTimeout());
                        session.open();
                        BindRequest request;
                        BindResponse response;
                        switch (smpp.getBindOption()) {
                            case receiver:
                                request = new BindReceiver();
                                break;
                            case transceiver:
                                request = new BindTransciever();
                                break;
                            case transmitter:
                                request = new BindTransmitter();
                                break;
                            default:
                                throw new IllegalArgumentException("Invalid bind option " + smpp.getBindOption().name());
                        }

                        // set values
                        request.setSystemId(smpp.getSystemId());
                        request.setPassword(smpp.getPassword());
                        request.setSystemType(smpp.getSystemType());

                        // send the bind request
                        try {
                            response = session.bind(request);
                        } catch (NullPointerException npe) {
                            response = null;
                        }
                        String error = "";
                        if (response != null) {
                            switch (response.getCommandStatus()) {
                                case Data.ESME_RALYBND:
                                case Data.ESME_ROK:
                                    success = true;
                                    updateBind(smpp.getName());
                                    setDescription(smpp);
                                    log.info("Bind successful for {}:{} [systemId={}, bindOption={}]", smpp.getHost(), smpp.getPort(), smpp.getSystemId(), smpp.getBindOption());
                                    break;
                                case Data.ESME_RINVPASWD:
                                    error = "(Invalid Password)";
                                    break;
                                case Data.ESME_RINVSYSID:
                                    error = "(Invalid system id)";
                                    break;
                                case Data.ESME_RINVSERTYP:
                                    error = "(Invalid service type)";
                                    break;
                                case Data.ESME_RBINDFAIL:
                                    error = "(Bind failed)";
                                    break;
                            }
                            if (!success) {
                                log.info("Bind failed for {}:{} [systemId={}, bindOption={}, error={}, responseStatus={}]", smpp.getHost(), smpp.getPort(), smpp.getSystemId(), smpp.getBindOption(), error, response.getCommandStatus());
                                log.debug("Bind failure details: host={}, port={}, systemId={}, bindOption={}, error={}, responseStatus={}, response={}",
                                        smpp.getHost(), smpp.getPort(), smpp.getSystemId(), smpp.getBindOption(), error, response.getCommandStatus(), response.debugString());
                            }
                        } else {
                            error = "No response";
                            log.info("No response on bind for {}:{} [systemId={}, bindOption={}, error={}]", smpp.getHost(), smpp.getPort(), smpp.getSystemId(), smpp.getBindOption(), error);
                            log.debug("Bind failure details: host={}, port={}, systemId={}, bindOption={}, error={}", smpp.getHost(), smpp.getPort(), smpp.getSystemId(), smpp.getBindOption(), error);
                        }
                    } catch (Exception e) {
                        log.info("Bind exception for {}:{} [systemId={}, bindOption={}, errorType={}, message={}]", smpp.getHost(), smpp.getPort(), smpp.getSystemId(), smpp.getBindOption(), e.getClass().getSimpleName(), e.getMessage());
                        log.debug("Bind exception details: host={}, port={}, systemId={}, bindOption={}, exception={}", smpp.getHost(), smpp.getPort(), smpp.getSystemId(), smpp.getBindOption(), e, e);
                    }
                    processOnStartListeners(success);
                    if (!success && !requestingStop) {
                        try {
                            long wait_time = WAIT_BEFORE_REBIND;
                            if (bindAttempts > MAX_BIND_ATTEMPTS_BEFORE_LONG_SLEEP) {
                                wait_time = MAX_BIND_ATTEMPTS_FACTOR * MAX_BIND_ATTEMPTS_BEFORE_LONG_SLEEP;
                                bindAttempts = 0;
                            }
                            log.debug("Waiting {} ms before next bind attempt for {}:{} [systemId={}, bindOption={}]", wait_time, smpp.getHost(), smpp.getPort(), smpp.getSystemId(), smpp.getBindOption());
                            lockObject.wait(wait_time);
                        } catch (InterruptedException e) {
                            log.warn("Bind wait interrupted", e);
                        }
                    }
                }
            }
            return state;
        } finally {
        }
    }

    @Override
    public boolean closeConnection(boolean sendUnbind) {
        boolean result = false;
        synchronized (lockObject) {
            try {
                if (state == State.unbound) {
                    return false;
                }
                state = State.unbinding;
                if (sendUnbind && session != null && session.isBound())
                    result = sendUnbind();

                shutDownSession();
                processOnCloseListeners(result);
            } finally {
                state = State.unbound;
            }
        }
        return result;
    }

    @Override
    public void shutDown() {
        requestingStop = true;
        synchronized (lockObject) {
            try {
                if (!state.isClosable()) {
                    return;
                }
                state = State.unbinding;
                closeConnection(true);
                shutDownSession();
            } finally {
                requestingStop = false;
            }
        }
    }

    public boolean sendUnbind() {
        boolean result = false;
        if (session != null && session.getReceiver() != null) {
            synchronized (lockObject) {
                if (session != null && session.getReceiver() != null) {
                    session.getReceiver().setServerPDUEventListener(null); // switch to synchronise mode
                    try {
                        UnbindResp response = session.unbind();
                        if (response != null) {
                            if (response.getCommandStatus() != Data.ESME_ROK) {

                            } else {
                                result = true;
                            }
                        } else {

                        }
                    } catch (Exception e) {

                    }
                }
            }
        }
        return result;
    }

    @Override
    public Response send(PDU pdu) throws IOException {
        pdu.assignSequenceNumber();
        Integer id = pdu.getSequenceNumber();//We assume that we won't get the same SequenceNumber withing a safe period of time.
        requests.put(id, id);//marking that we are waiting for the response to this PDU
        if (session != null) {
            synchronized (lockObject) {
                //instead of 'if (session != null)' here, added catch NullPointerException below.

                // we put this check inside if bind was changed by start or close connection
                if (state != State.bound)
                    throw new IOException("Connection is not bound");
                try {
                    session.getTransmitter().send(pdu);
                } catch (IOException e){
                    this.state = State.unbound;
                    throw e;
                } catch (ValueNotSetException | NullPointerException e) {
                    throw new IOException(e);
                }
            }
            try {
                requests.waitForResponse(id); //waiting for the response to this PDU
            } catch (InterruptedException e) {

            }
            return requests.remove(Response.class, id);//removing the marking,
        }
        throw new IOException("Session is null");
    }

    public void respond(Response response) throws IOException {
        if (session != null) {
            synchronized (lockObject) {
                if (session != null) {
                    try {
                        session.respond(response);
                    } catch (ValueNotSetException | WrongSessionStateException e) {
                        throw new IOException(e);
                    }
                }
            }
        } else {
            throw new IOException("Session is null");
        }
    }
}