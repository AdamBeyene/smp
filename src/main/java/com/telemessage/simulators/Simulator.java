package com.telemessage.simulators;

import com.telemessage.simulators.conf.AbstractConnection;

import java.util.Map;


public interface Simulator {
    public void shutdown();
    public void start();
    public <T extends AbstractConnection> Map<Integer, T> getConnections();
}
