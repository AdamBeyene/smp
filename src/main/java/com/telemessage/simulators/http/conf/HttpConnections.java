package com.telemessage.simulators.http.conf;

import com.telemessage.simulators.conf.AbstractConnections;
import com.telemessage.simulators.http.HttpConnection;
import lombok.extern.slf4j.Slf4j;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

@Slf4j
@Root(name = "connections")
public class HttpConnections extends AbstractConnections<HttpConnection> {


    @ElementList(inline = true, required = false, entry = "connection")
    public List<HttpConnection> getDispatchers() {
        return this.getConnections();
    }

    @ElementList(inline = true, required = false, entry = "connection")
    public void setDispatchers(List<HttpConnection> connections) {
        this.setConnections(connections);
    }

}
