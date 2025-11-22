package com.telemessage.simulators.web;

import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.simulators.controllers.utils.Utils;
import com.telemessage.simulators.stats.ConnectionMetrics;
import com.telemessage.simulators.stats.MessagingMetrics;
import com.telemessage.simulators.web.wrappers.HttpWebConnection;
import com.telemessage.simulators.web.wrappers.SMPPWebConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * V2 Controller for modern Thymeleaf-based pages with Cloudhopper support.
 * Provides enhanced UI with 100% feature parity plus new capabilities.
 */
@Slf4j
@Controller
@RequestMapping("/")
public class V2Controller {

    @Autowired
    private MessagesCache messagesCache;

    /**
     * Renders the modern connections V2 page with enhanced features.
     */
    @GetMapping("/connectionsV2")
    public String connectionsV2(Model model) {
        // Get SMPP connections
        SMPPWebConnection[] smppConnections = Utils.smppInfoConnections();
        HttpWebConnection[] httpConnections = Utils.httpInfoConnections();

        // Combine all connections for the view
        List<Map<String, Object>> allConnections = new ArrayList<>();

        // Process SMPP connections
        for (SMPPWebConnection conn : smppConnections) {
            Map<String, Object> connMap = new HashMap<>();
            connMap.put("id", conn.getId());
            connMap.put("name", conn.getName());
            connMap.put("type", "SMPP");

            String txState = conn.getTransmitter() != null ? conn.getTransmitter().getState() : "NA";
            String rxState = conn.getReceiver() != null ? conn.getReceiver().getState() : "NA";

            log.info("Connection {} - TX state: '{}' ({}), RX state: '{}' ({})",
                conn.getId(),
                txState, txState.getClass().getName(),
                rxState, rxState.getClass().getName());

            connMap.put("transmitterState", txState);
            connMap.put("receiverState", rxState);
            connMap.put("transmitterType", conn.getTransmitter() != null ? conn.getTransmitter().getType() : "NA");
            connMap.put("receiverType", conn.getReceiver() != null ? conn.getReceiver().getType() : "NA");
            connMap.put("transmitterPort", conn.getTransmitter() != null ? conn.getTransmitter().getPort() : 0);
            connMap.put("receiverPort", conn.getReceiver() != null ? conn.getReceiver().getPort() : 0);
            connMap.put("transmitterHost", conn.getTransmitter() != null ? conn.getTransmitter().getHost() : "NA");
            connMap.put("receiverHost", conn.getReceiver() != null ? conn.getReceiver().getHost() : "NA");
            // Note: extraInfo not directly available in SMPPWebConnection

            // Add Cloudhopper-specific metrics if available
            if (isCloudhopper()) {
                connMap.put("enquireLinkStatus", "OK"); // TODO: Get actual status
                connMap.put("reconnectCount", 0); // TODO: Get actual count
                connMap.put("throughput", 0.0); // TODO: Get actual throughput
            }

            allConnections.add(connMap);
        }

        // Process HTTP connections
        for (HttpWebConnection conn : httpConnections) {
            Map<String, Object> connMap = new HashMap<>();
            connMap.put("id", conn.getId());
            connMap.put("name", conn.getName());
            connMap.put("type", "HTTP");
            connMap.put("transmitterState", conn.isStarted() ? "Active" : "Inactive");
            connMap.put("receiverState", "NA");  // HTTP doesn't have separate receiver
            connMap.put("transmitterType", "HTTP");
            connMap.put("receiverType", "NA");
            connMap.put("transmitterPort", 0); // HTTP connections don't show port in this view
            connMap.put("receiverPort", 0);
            connMap.put("transmitterHost", conn.getUrl() != null ? conn.getUrl() : "NA");
            connMap.put("receiverHost", "NA");
            connMap.put("drURL", conn.getDrUrl());
            connMap.put("inUrl", conn.getInUrl());
            connMap.put("url", conn.getUrl());

            allConnections.add(connMap);
        }

        model.addAttribute("connections", allConnections);
        model.addAttribute("isCloudhopper", isCloudhopper());
        model.addAttribute("currentTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // Add metrics
        ConnectionMetrics metrics = getConnectionMetrics(smppConnections, httpConnections);
        model.addAttribute("metrics", metrics);

        return "connectionsV2";
    }

    /**
     * Renders the SMPP info page with connection types reference.
     */
    @GetMapping("/infoV2")
    public String infoV2(Model model) {
        model.addAttribute("isCloudhopper", isCloudhopper());
        return "infoV2";
    }

    /**
     * Renders the modern messages V2 page with enhanced features.
     */
    @GetMapping("/messagesV2")
    public String messagesV2(
            Model model,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String filter) {

        // Get messages from cache (using the map values)
        List<MessagesObject> allMessages = new ArrayList<>(messagesCache.getMap().values());

        // Apply search/filter if provided
        if (search != null && !search.isEmpty()) {
            allMessages = filterMessages(allMessages, search);
        }

        // Sort by time (newest first)
        allMessages.sort((a, b) -> {
            if (b.getMessageTime() == null) return -1;
            if (a.getMessageTime() == null) return 1;
            return b.getMessageTime().compareTo(a.getMessageTime());
        });

        // Calculate pagination
        int totalMessages = allMessages.size();
        int totalPages = (int) Math.ceil((double) totalMessages / size);
        page = Math.max(1, Math.min(page, totalPages)); // Ensure page is within bounds
        int start = (page - 1) * size;
        int end = Math.min(start + size, totalMessages);

        List<MessagesObject> pageMessages = start < totalMessages ?
            allMessages.subList(start, end) : new ArrayList<>();

        model.addAttribute("messages", pageMessages);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalMessages", totalMessages);
        model.addAttribute("search", search);
        model.addAttribute("filter", filter);
        model.addAttribute("isCloudhopper", isCloudhopper());

        // Add messaging metrics
        MessagingMetrics metrics = getMessagingMetrics(allMessages);
        model.addAttribute("metrics", metrics);

        return "messagesV2";
    }

    /**
     * API endpoint for grouped connections (for JavaScript AJAX calls).
     */
    @GetMapping("/api/v2/connections/grouped")
    @ResponseBody
    public Map<String, Object> getGroupedConnections() {
        SMPPWebConnection[] smppConnections = Utils.smppInfoConnections();
        HttpWebConnection[] httpConnections = Utils.httpInfoConnections();

        // Group connections by family (base name without suffix)
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();

        // Group SMPP connections
        for (SMPPWebConnection conn : smppConnections) {
            String familyName = extractFamilyName(conn.getName());
            Map<String, Object> connData = new HashMap<>();
            connData.put("id", conn.getId());
            connData.put("name", conn.getName());
            connData.put("type", "SMPP");
            connData.put("transmitterState", conn.getTransmitter() != null ? conn.getTransmitter().getState() : "NA");
            connData.put("receiverState", conn.getReceiver() != null ? conn.getReceiver().getState() : "NA");

            grouped.computeIfAbsent(familyName, k -> new ArrayList<>()).add(connData);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("grouped", grouped);
        result.put("totalFamilies", grouped.size());
        result.put("totalConnections", smppConnections.length + httpConnections.length);

        return result;
    }

    /**
     * API endpoint for connection metrics.
     */
    @GetMapping("/api/v2/metrics/connections")
    @ResponseBody
    public ConnectionMetrics getConnectionMetricsApi() {
        SMPPWebConnection[] smppConnections = Utils.smppInfoConnections();
        HttpWebConnection[] httpConnections = Utils.httpInfoConnections();
        return getConnectionMetrics(smppConnections, httpConnections);
    }

    /**
     * API endpoint for messaging metrics.
     */
    @GetMapping("/api/v2/metrics/messages")
    @ResponseBody
    public MessagingMetrics getMessagingMetricsApi() {
        List<MessagesObject> allMessages = new ArrayList<>(messagesCache.getMap().values());
        return getMessagingMetrics(allMessages);
    }

    /**
     * API endpoint for advanced message search.
     */
    @PostMapping("/api/v2/messages/search")
    @ResponseBody
    public Map<String, Object> advancedSearch(@RequestBody SearchRequest request) {
        List<MessagesObject> messages = new ArrayList<>(messagesCache.getMap().values());

        // Apply all search criteria
        if (request.text != null && !request.text.isEmpty()) {
            messages = messages.stream()
                .filter(m -> m.getText() != null && m.getText().contains(request.text))
                .collect(Collectors.toList());
        }

        if (request.from != null && !request.from.isEmpty()) {
            messages = messages.stream()
                .filter(m -> m.getFrom() != null && m.getFrom().contains(request.from))
                .collect(Collectors.toList());
        }

        if (request.to != null && !request.to.isEmpty()) {
            messages = messages.stream()
                .filter(m -> m.getTo() != null && m.getTo().contains(request.to))
                .collect(Collectors.toList());
        }

        if (request.direction != null && !request.direction.equals("Any")) {
            messages = messages.stream()
                .filter(m -> m.getDir() != null && m.getDir().equalsIgnoreCase(request.direction))
                .collect(Collectors.toList());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("messages", messages);
        result.put("count", messages.size());

        return result;
    }

    // Helper methods

    private boolean isCloudhopper() {
        // Check if Cloudhopper is enabled
        String property = System.getProperty("cloudhopper.enable");
        if (property == null) {
            property = System.getenv("CLOUDHOPPER_ENABLE");
        }
        return "true".equalsIgnoreCase(property);
    }

    private ConnectionMetrics getConnectionMetrics(SMPPWebConnection[] smppConnections, HttpWebConnection[] httpConnections) {
        ConnectionMetrics metrics = new ConnectionMetrics();

        metrics.totalConnections = smppConnections.length + httpConnections.length;
        metrics.smppConnections = smppConnections.length;
        metrics.httpConnections = httpConnections.length;
        metrics.activeConnections = 0;
        metrics.inactiveConnections = 0;

        // Count active SMPP connections
        for (SMPPWebConnection conn : smppConnections) {
            boolean isActive = false;
            if (conn.getTransmitter() != null && "Bound".equals(conn.getTransmitter().getState())) {
                isActive = true;
                metrics.boundTransmitters++;
            }
            if (conn.getReceiver() != null && "Bound".equals(conn.getReceiver().getState())) {
                isActive = true;
                metrics.boundReceivers++;
            }
            if (isActive) {
                metrics.activeConnections++;
            } else {
                metrics.inactiveConnections++;
            }
        }

        // Add HTTP connections to active count
        metrics.activeConnections += httpConnections.length;

        metrics.messagesPerSecond = calculateMessagesPerSecond();
        metrics.averageLatency = calculateAverageLatency();

        return metrics;
    }

    private MessagingMetrics getMessagingMetrics(List<MessagesObject> messages) {
        MessagingMetrics metrics = new MessagingMetrics();

        metrics.totalMessages = messages.size();
        metrics.inboundMessages = (int) messages.stream()
            .filter(m -> "In".equalsIgnoreCase(m.getDir()))
            .count();
        metrics.outboundMessages = (int) messages.stream()
            .filter(m -> "Out".equalsIgnoreCase(m.getDir()))
            .count();
        metrics.deliveryReceipts = (int) messages.stream()
            .filter(m -> m.getDeliveryReceiptShortMessage() != null)
            .count();

        // Encoding distribution
        metrics.encodingDistribution = new HashMap<>();
        for (MessagesObject msg : messages) {
            String encoding = msg.getMessageEncoding() != null ? msg.getMessageEncoding() : "Unknown";
            metrics.encodingDistribution.merge(encoding, 1, Integer::sum);
        }

        // Provider distribution
        metrics.providerDistribution = new HashMap<>();
        for (MessagesObject msg : messages) {
            String provider = msg.getProviderId() != null ? msg.getProviderId() : "Unknown";
            metrics.providerDistribution.merge(provider, 1, Integer::sum);
        }

        return metrics;
    }

    private double calculateMessagesPerSecond() {
        // TODO: Implement actual calculation based on recent messages
        return 0.0;
    }

    private double calculateAverageLatency() {
        // TODO: Implement actual calculation based on message timestamps
        return 0.0;
    }

    private List<MessagesObject> filterMessages(List<MessagesObject> messages, String search) {
        String searchLower = search.toLowerCase();
        return messages.stream()
            .filter(m ->
                (m.getText() != null && m.getText().toLowerCase().contains(searchLower)) ||
                (m.getFrom() != null && m.getFrom().toLowerCase().contains(searchLower)) ||
                (m.getTo() != null && m.getTo().toLowerCase().contains(searchLower)) ||
                (m.getId() != null && m.getId().toLowerCase().contains(searchLower)) ||
                (m.getProviderId() != null && m.getProviderId().toLowerCase().contains(searchLower))
            )
            .collect(Collectors.toList());
    }

    private String extractFamilyName(String connectionName) {
        // Extract base name for grouping (e.g., "conn_1" -> "conn")
        if (connectionName == null) return "default";
        int underscoreIndex = connectionName.lastIndexOf('_');
        if (underscoreIndex > 0) {
            String suffix = connectionName.substring(underscoreIndex + 1);
            if (suffix.matches("\\d+")) {
                return connectionName.substring(0, underscoreIndex);
            }
        }
        return connectionName;
    }

    // Request classes for API endpoints
    public static class SearchRequest {
        public String text;
        public String from;
        public String to;
        public String direction;
        public String providerId;
        public String messageId;
        public boolean includeDeliveryReceipts;
    }
}