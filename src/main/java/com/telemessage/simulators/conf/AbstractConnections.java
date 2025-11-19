package com.telemessage.simulators.conf;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Setter
@Getter
@Slf4j
public class AbstractConnections<T extends AbstractConnection> {

    protected List<T> connections;

}
