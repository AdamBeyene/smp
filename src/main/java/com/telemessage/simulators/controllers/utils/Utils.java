package com.telemessage.simulators.controllers.utils;

import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.http.HttpConnection;
import com.telemessage.simulators.http.HttpSimulator;
import com.telemessage.simulators.smpp.SMPPSimulatorInterface;
import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import com.telemessage.simulators.web.wrappers.HttpWebConnection;
import com.telemessage.simulators.web.wrappers.SMPPWebConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Component
public class Utils {

    EnvConfiguration conf;

    static SMPPSimulatorInterface smppSim;
    static HttpSimulator httpSim;

    @Autowired
    public Utils(EnvConfiguration conf,
                 @Qualifier("smppSimulator") SMPPSimulatorInterface smppSim,
                 HttpSimulator httpSim
    ) {
        this.conf = conf;
        this.smppSim = smppSim;
        this.httpSim = httpSim;
    }

    public static SMPPWebConnection[] smppInfoConnections() {
        List<SMPPWebConnection> conns = new ArrayList<>();
        List<SMPPConnectionConf> cs = new ArrayList<>(smppSim.getConnections().values());
        Collections.sort(cs, new Comparator<SMPPConnectionConf>() {
            @Override
            public int compare(SMPPConnectionConf o1, SMPPConnectionConf o2) {
                if (o1 == o2) return 0;
                if (o1 == null) return -1;
                if (o2 == null) return 1;
                return o1.getId() - o2.getId();
            }
        });
        for (SMPPConnectionConf c : cs) {
            conns.add(new SMPPWebConnection(c));
        }
        return conns.toArray(new SMPPWebConnection[conns.size()]);
    }

    public static HttpWebConnection[] httpInfoConnections() {
        List<HttpConnection> cs = new ArrayList<>(httpSim.getConnections().values());
        Collections.sort(cs, new Comparator<HttpConnection>() {
            @Override
            public int compare(HttpConnection o1, HttpConnection o2) {
                if (o1 == o2) return 0;
                if (o1 == null) return -1;
                if (o2 == null) return 1;
                return o1.getId() - o2.getId();
            }
        });
        List<HttpWebConnection> conns = new ArrayList<>();
        for (HttpConnection c : cs) {
            conns.add(new HttpWebConnection(c));
        }
        return conns.toArray(new HttpWebConnection[conns.size()]);
    }
}
