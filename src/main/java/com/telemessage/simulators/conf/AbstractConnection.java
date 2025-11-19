package com.telemessage.simulators.conf;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;

@Setter
@Getter
@Slf4j
public abstract class AbstractConnection {

    @Element(required = false, name = "automatic_dr") protected String automaticDR;
    @Element(required = false, name = "direct_status") protected String directStatus;
    @Attribute protected int id;
    @Element protected String name;

}
