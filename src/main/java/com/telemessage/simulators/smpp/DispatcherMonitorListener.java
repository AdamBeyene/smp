package com.telemessage.simulators.smpp;

import java.util.EventListener;

public interface DispatcherMonitorListener extends EventListener {
    public void onMonitorFailure();
}