package com.telemessage.simulators.smpp.conf;

import com.telemessage.simulators.conf.AbstractConnections;
import lombok.extern.slf4j.Slf4j;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

@Slf4j
@Root(name = "connections")
public class SMPPConnections extends AbstractConnections<SMPPConnectionConf> {

    @Override
    @ElementList(inline = true, required = false, entry = "connection")
    public List<SMPPConnectionConf> getConnections() {
        return super.getConnections();
    }

    @Override
    @ElementList(inline = true, required = false, entry = "connection")
    public void setConnections(List<SMPPConnectionConf> connections) {
        super.setConnections(connections);
    }
}
