package com.telemessage.simulators.smpp;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AlreadyBoundException extends Exception {

    public AlreadyBoundException(String message) {
        super(message);
    }
}