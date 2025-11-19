package com.telemessage.simulators.web.wrappers;


import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Getter
@Slf4j
public class WebConnection {

    protected int id;
    protected String name;

    public WebConnection() {
    }

    public WebConnection(int id, String name) {
        this.id = id;
        this.name = name;
    }

}
