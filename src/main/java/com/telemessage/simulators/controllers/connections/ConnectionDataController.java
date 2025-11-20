package com.telemessage.simulators.controllers.connections;


import com.telemessage.simulators.common.JSONUtils;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.utils.Utils;
import com.telemessage.simulators.http.HttpConnection;
import com.telemessage.simulators.http.HttpSimulator;
import com.telemessage.simulators.http.conf.HttpConnections;
import com.telemessage.simulators.smpp.SMPPSimulatorInterface;
import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import com.telemessage.simulators.smpp.conf.SMPPConnections;
import com.telemessage.simulators.web.wrappers.HttpWebConnection;
import com.telemessage.simulators.web.wrappers.SMPPWebConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/connections")
public class ConnectionDataController {

    EnvConfiguration conf;
    SMPPSimulatorInterface smppConns;
    HttpSimulator httpConns;

    @Autowired
    public ConnectionDataController(EnvConfiguration conf, @Qualifier("smppSimulator") SMPPSimulatorInterface smppConns, HttpSimulator httpConns) {
        this.conf = conf;
        this.smppConns = smppConns;
        this.httpConns = httpConns;
    }

    @GetMapping("")
    public String getConnections(Model model){
        SMPPConnections smppConnMap = smppConns.getConns();
        HttpConnections httpConnMap = httpConns.getConns();

        SMPPWebConnection[] smppConnections = Utils.smppInfoConnections();
        HttpWebConnection[] httpConnections = Utils.httpInfoConnections();

        List<AggregatedConnectionData> aggList = new ArrayList<>();

        // SMPP connections
        for (SMPPWebConnection conn : smppConnections) {
            SMPPConnectionConf conf = smppConnMap.getConnections().stream()
                    .filter(c -> c.getId() == conn.getId())
                    .findFirst()
                    .orElse(null);

            List<Map<String, String>> extraInfo = new ArrayList<>();

            // SMPP Transmitter fields
            if (conf != null && conf.getTransmitter() != null) {
                Map<String, String> tr = new java.util.LinkedHashMap<>();
                tr.put("transmitter:concatType", conf.getTransmitter().getConcatenation().name());
                tr.put("transmitter:threads", String.valueOf(conf.getTransmitter().getThreads()));
                tr.put("transmitter:systemId", conf.getTransmitter().getSystemId());
                tr.put("transmitter:systemType", conf.getTransmitter().getSystemType());
                tr.put("transmitter:encoding", conf.getTransmitter().getEncoding());
                tr.put("transmitter:password", conf.getTransmitter().getPassword());
                tr.put("transmitter:automaticDR", conf.getAutomaticDR());
                tr.put("transmitter:directStatus", conf.getDirectStatus());
                tr.put("----------", "----------");
                extraInfo.add(tr);
            }

            // SMPP Receiver fields
            if (conf != null && conf.getReceiver() != null) {
                Map<String, String> rc = new java.util.LinkedHashMap<>();
                rc.put("receiver:concatType", conf.getReceiver().getConcatenation().name());
                rc.put("receiver:threads", String.valueOf(conf.getReceiver().getThreads()));
                rc.put("receiver:systemId", conf.getReceiver().getSystemId());
                rc.put("receiver:systemType", conf.getReceiver().getSystemType());
                rc.put("receiver:encoding", conf.getReceiver().getEncoding());
                rc.put("receiver:password", conf.getReceiver().getPassword());
                rc.put("receiver:automaticDR", conf.getAutomaticDR());
                rc.put("receiver:directStatus", conf.getDirectStatus());
                extraInfo.add(rc);
            }
            aggList.add(new AggregatedConnectionData(conn, extraInfo));
        }

        // HTTP connections
        for (HttpWebConnection conn : httpConnections) {
            HttpConnection httpConf = httpConnMap.getConnections().stream()
                    .filter(c -> c.getId() == conn.getId())
                    .findFirst()
                    .orElse(null);

            // Prepare extra info for HTTP connections
            List<Map<String, String>> extraInfo = new ArrayList<>();
            if (httpConf != null) {
                Map<String, String> http = new java.util.LinkedHashMap<>();
                http.put("http:drURL", httpConf.getDrURL());
                http.put("http:drFromIP", httpConf.getDrFromIP());
                http.put("http:inUrl", httpConf.getInUrl());
                http.put("http:automaticDR", httpConf.getAutomaticDR());
                http.put("http:dr_from_ip", httpConf.getDrFromIP());
                http.put("http:directStatus", httpConf.getDirectStatus());
                http.put("http:httpMethod", httpConf.getHttpMethod());
                extraInfo.add(http);
            }
            aggList.add(new AggregatedConnectionData(conn, extraInfo));
        }

        model.addAttribute("data", JSONUtils.toJSON(aggList));
        return "pages/connections";
    }

    /**
     * Get connections grouped by family (related connections like 379 and 37900)
     * Groups connections by base ID and name
     */
    @GetMapping("/grouped")
    @ResponseBody
    public List<ConnectionGroupResponse> getConnectionsGrouped() {
        List<AggregatedConnectionData> allConnections = getAllConnectionsData();
        return groupConnectionsByFamily(allConnections);
    }

    /**
     * Helper method to get all connections data (reusable)
     */
    private List<AggregatedConnectionData> getAllConnectionsData() {
        SMPPConnections smppConnMap = smppConns.getConns();
        HttpConnections httpConnMap = httpConns.getConns();

        SMPPWebConnection[] smppConnections = Utils.smppInfoConnections();
        HttpWebConnection[] httpConnections = Utils.httpInfoConnections();

        List<AggregatedConnectionData> aggList = new ArrayList<>();

        // SMPP connections
        for (SMPPWebConnection conn : smppConnections) {
            SMPPConnectionConf conf = smppConnMap.getConnections().stream()
                    .filter(c -> c.getId() == conn.getId())
                    .findFirst()
                    .orElse(null);

            List<Map<String, String>> extraInfo = new ArrayList<>();

            if (conf != null && conf.getTransmitter() != null) {
                Map<String, String> tr = new java.util.LinkedHashMap<>();
                tr.put("transmitter:concatType", conf.getTransmitter().getConcatenation().name());
                tr.put("transmitter:threads", String.valueOf(conf.getTransmitter().getThreads()));
                tr.put("transmitter:systemId", conf.getTransmitter().getSystemId());
                tr.put("transmitter:systemType", conf.getTransmitter().getSystemType());
                tr.put("transmitter:encoding", conf.getTransmitter().getEncoding());
                tr.put("transmitter:password", conf.getTransmitter().getPassword());
                tr.put("transmitter:automaticDR", conf.getAutomaticDR());
                tr.put("transmitter:directStatus", conf.getDirectStatus());
                tr.put("----------", "----------");
                extraInfo.add(tr);
            }

            if (conf != null && conf.getReceiver() != null) {
                Map<String, String> rc = new java.util.LinkedHashMap<>();
                rc.put("receiver:concatType", conf.getReceiver().getConcatenation().name());
                rc.put("receiver:threads", String.valueOf(conf.getReceiver().getThreads()));
                rc.put("receiver:systemId", conf.getReceiver().getSystemId());
                rc.put("receiver:systemType", conf.getReceiver().getSystemType());
                rc.put("receiver:encoding", conf.getReceiver().getEncoding());
                rc.put("receiver:password", conf.getReceiver().getPassword());
                rc.put("receiver:automaticDR", conf.getAutomaticDR());
                rc.put("receiver:directStatus", conf.getDirectStatus());
                extraInfo.add(rc);
            }
            aggList.add(new AggregatedConnectionData(conn, extraInfo));
        }

        // HTTP connections
        for (HttpWebConnection conn : httpConnections) {
            HttpConnection httpConf = httpConnMap.getConnections().stream()
                    .filter(c -> c.getId() == conn.getId())
                    .findFirst()
                    .orElse(null);

            List<Map<String, String>> extraInfo = new ArrayList<>();
            if (httpConf != null) {
                Map<String, String> http = new java.util.LinkedHashMap<>();
                http.put("http:drURL", httpConf.getDrURL());
                http.put("http:drFromIP", httpConf.getDrFromIP());
                http.put("http:inUrl", httpConf.getInUrl());
                http.put("http:automaticDR", httpConf.getAutomaticDR());
                http.put("http:dr_from_ip", httpConf.getDrFromIP());
                http.put("http:directStatus", httpConf.getDirectStatus());
                http.put("http:httpMethod", httpConf.getHttpMethod());
                extraInfo.add(http);
            }
            aggList.add(new AggregatedConnectionData(conn, extraInfo));
        }

        return aggList;
    }

    /**
     * Group connections by family based on name and base ID
     * Example: 379 and 37900 with same name "CLX USA" become a family
     */
    private List<ConnectionGroupResponse> groupConnectionsByFamily(List<AggregatedConnectionData> connections) {
        // Group by name first
        Map<String, List<AggregatedConnectionData>> grouped = connections.stream()
                .collect(Collectors.groupingBy(conn -> conn.name != null ? conn.name : "Unknown"));

        List<ConnectionGroupResponse> result = new ArrayList<>();

        for (Map.Entry<String, List<AggregatedConnectionData>> entry : grouped.entrySet()) {
            String groupName = entry.getKey();
            List<AggregatedConnectionData> members = entry.getValue();

            if (members.size() > 1) {
                // Family of related connections
                members.sort(Comparator.comparing(c -> c.id));

                AggregatedConnectionData primary = members.get(0);
                String baseId = extractBaseId(primary.id);

                // Calculate status summary
                long boundTx = members.stream()
                        .filter(c -> "Bound".equals(c.transmitterState))
                        .count();
                long boundRx = members.stream()
                        .filter(c -> "Bound".equals(c.receiverState))
                        .count();

                String statusSummary;
                if (boundTx == members.size() && boundRx == members.size()) {
                    statusSummary = "All Bound";
                } else if (boundTx == 0 && boundRx == 0) {
                    statusSummary = "All Unbound";
                } else {
                    statusSummary = "Mixed";
                }

                ConnectionGroupResponse.GroupMetadata metadata = ConnectionGroupResponse.GroupMetadata.builder()
                        .baseId(baseId)
                        .groupName(groupName)
                        .memberCount(members.size())
                        .statusSummary(statusSummary)
                        .boundTransmitters((int) boundTx)
                        .boundReceivers((int) boundRx)
                        .build();

                ConnectionGroupResponse group = ConnectionGroupResponse.builder()
                        .type("family")
                        .connection(primary)
                        .members(members)
                        .metadata(metadata)
                        .build();

                result.add(group);
            } else {
                // Single connection
                ConnectionGroupResponse single = ConnectionGroupResponse.builder()
                        .type("single")
                        .connection(members.get(0))
                        .members(null)
                        .metadata(null)
                        .build();

                result.add(single);
            }
        }

        // Sort by group name
        result.sort(Comparator.comparing(g ->
            g.getMetadata() != null ? g.getMetadata().getGroupName() : g.getConnection().name
        ));

        return result;
    }

    /**
     * Extract base ID from connection ID
     * Example: 379 -> "379", 37900 -> "379"
     */
    private String extractBaseId(Integer id) {
        if (id == null) return "0";
        String idStr = String.valueOf(id);
        // Remove trailing "00", "01", "02" etc if present
        if (idStr.length() > 3 && idStr.endsWith("00")) {
            return idStr.substring(0, idStr.length() - 2);
        }
        return idStr;
    }
}
