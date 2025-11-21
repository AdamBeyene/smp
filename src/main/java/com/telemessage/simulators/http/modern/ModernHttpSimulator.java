package com.telemessage.simulators.http.modern;

import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Modernized HTTP Simulator using Spring RestController and RestAssured.
 *
 * Features:
 * - RESTful API design
 * - Async message processing
 * - Multiple provider support (GCM, Cellcom, MIRS, etc.)
 * - Integration with both Logica and Cloudhopper SMPP
 * - Comprehensive error handling
 * - Request/Response logging
 * - Metrics and monitoring
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-21
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/http-sim")
public class ModernHttpSimulator {

    @Autowired
    private MessagesCache messagesCache;

    @Autowired(required = false)
    private HttpProviderRegistry providerRegistry;

    // Metrics
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final Map<String, AtomicLong> providerMetrics = new ConcurrentHashMap<>();

    // Active sessions
    private final Map<String, HttpSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Send an SMS message via HTTP.
     */
    @PostMapping("/send")
    public ResponseEntity<HttpSendResponse> sendMessage(@RequestBody HttpSendRequest request) {
        totalRequests.incrementAndGet();
        String requestId = UUID.randomUUID().toString();

        log.info("HTTP send request {} from provider: {}", requestId, request.getProvider());

        try {
            // Validate request
            ValidationResult validation = validateRequest(request);
            if (!validation.isValid()) {
                failedRequests.incrementAndGet();
                return ResponseEntity.badRequest().body(
                    HttpSendResponse.error(requestId, validation.getErrorMessage())
                );
            }

            // Get provider handler
            HttpProviderHandler handler = getProviderHandler(request.getProvider());
            if (handler == null) {
                failedRequests.incrementAndGet();
                return ResponseEntity.badRequest().body(
                    HttpSendResponse.error(requestId, "Unknown provider: " + request.getProvider())
                );
            }

            // Process message
            CompletableFuture<HttpSendResult> future = processMessageAsync(request, handler);

            // Wait for result (with timeout)
            HttpSendResult result = future.get(30, TimeUnit.SECONDS);

            if (result.isSuccess()) {
                successfulRequests.incrementAndGet();
                updateProviderMetrics(request.getProvider(), true);

                // Cache message
                cacheHttpMessage(request, result);

                return ResponseEntity.ok(HttpSendResponse.success(
                    requestId,
                    result.getMessageId(),
                    "Message accepted for delivery"
                ));
            } else {
                failedRequests.incrementAndGet();
                updateProviderMetrics(request.getProvider(), false);

                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    HttpSendResponse.error(requestId, result.getErrorMessage())
                );
            }

        } catch (Exception e) {
            log.error("Error processing HTTP send request {}", requestId, e);
            failedRequests.incrementAndGet();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                HttpSendResponse.error(requestId, "Internal error: " + e.getMessage())
            );
        }
    }

    /**
     * Receive delivery receipt via HTTP.
     */
    @PostMapping("/delivery-receipt")
    public ResponseEntity<HttpDeliveryReceiptResponse> receiveDeliveryReceipt(
            @RequestBody HttpDeliveryReceiptRequest request) {

        String receiptId = UUID.randomUUID().toString();
        log.info("HTTP delivery receipt {} for message: {}", receiptId, request.getMessageId());

        try {
            // Find original message
            MessagesObject originalMessage = messagesCache.getMap().get(request.getMessageId());
            if (originalMessage == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    HttpDeliveryReceiptResponse.error(receiptId, "Message not found: " + request.getMessageId())
                );
            }

            // Update message with delivery receipt
            originalMessage.setDeliveryReceiptHttpMessage(request.toJson());
            originalMessage.setDeliveryReceiptTime(String.valueOf(System.currentTimeMillis()));

            // Process status
            processDeliveryStatus(originalMessage, request);

            messagesCache.setDirty(true);

            return ResponseEntity.ok(HttpDeliveryReceiptResponse.success(
                receiptId,
                "Delivery receipt processed"
            ));

        } catch (Exception e) {
            log.error("Error processing delivery receipt {}", receiptId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                HttpDeliveryReceiptResponse.error(receiptId, "Internal error: " + e.getMessage())
            );
        }
    }

    /**
     * Query message status.
     */
    @GetMapping("/status/{messageId}")
    public ResponseEntity<HttpStatusResponse> getMessageStatus(@PathVariable String messageId) {
        try {
            MessagesObject message = messagesCache.getMap().get(messageId);
            if (message == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    HttpStatusResponse.notFound(messageId)
                );
            }

            return ResponseEntity.ok(HttpStatusResponse.from(message));

        } catch (Exception e) {
            log.error("Error querying status for message {}", messageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                HttpStatusResponse.error(messageId, e.getMessage())
            );
        }
    }

    /**
     * Bulk send messages.
     */
    @PostMapping("/bulk-send")
    public ResponseEntity<HttpBulkSendResponse> bulkSend(@RequestBody HttpBulkSendRequest request) {
        String bulkId = UUID.randomUUID().toString();
        log.info("HTTP bulk send {} with {} messages", bulkId, request.getMessages().size());

        List<HttpSendResponse> results = new ArrayList<>();
        int successful = 0;
        int failed = 0;

        for (HttpSendRequest message : request.getMessages()) {
            ResponseEntity<HttpSendResponse> response = sendMessage(message);
            HttpSendResponse result = response.getBody();
            results.add(result);

            if (response.getStatusCode() == HttpStatus.OK) {
                successful++;
            } else {
                failed++;
            }
        }

        return ResponseEntity.ok(new HttpBulkSendResponse(
            bulkId,
            results.size(),
            successful,
            failed,
            results
        ));
    }

    /**
     * Get simulator metrics.
     */
    @GetMapping("/metrics")
    public ResponseEntity<HttpMetrics> getMetrics() {
        HttpMetrics metrics = new HttpMetrics();
        metrics.setTotalRequests(totalRequests.get());
        metrics.setSuccessfulRequests(successfulRequests.get());
        metrics.setFailedRequests(failedRequests.get());
        metrics.setActiveSessions(activeSessions.size());

        Map<String, Long> providerStats = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : providerMetrics.entrySet()) {
            providerStats.put(entry.getKey(), entry.getValue().get());
        }
        metrics.setProviderMetrics(providerStats);

        return ResponseEntity.ok(metrics);
    }

    /**
     * Forward message to external HTTP endpoint (outgoing).
     */
    @PostMapping("/forward")
    public ResponseEntity<HttpForwardResponse> forwardMessage(@RequestBody HttpForwardRequest request) {
        String forwardId = UUID.randomUUID().toString();
        log.info("Forwarding message {} to {}", forwardId, request.getTargetUrl());

        try {
            // Use RestAssured for external HTTP call
            RestAssured.baseURI = request.getTargetUrl();
            RequestSpecification spec = RestAssured.given()
                .contentType(request.getContentType())
                .headers(request.getHeaders());

            // Add authentication if provided
            if (request.getAuthType() != null) {
                switch (request.getAuthType()) {
                    case "basic":
                        spec.auth().basic(request.getUsername(), request.getPassword());
                        break;
                    case "bearer":
                        spec.auth().oauth2(request.getToken());
                        break;
                    case "api-key":
                        spec.header(request.getApiKeyHeader(), request.getApiKey());
                        break;
                }
            }

            // Send request
            Response response = spec
                .body(request.getPayload())
                .post();

            // Cache forwarded message
            cacheForwardedMessage(request, response);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                return ResponseEntity.ok(HttpForwardResponse.success(
                    forwardId,
                    response.getStatusCode(),
                    response.getBody().asString()
                ));
            } else {
                return ResponseEntity.status(response.getStatusCode()).body(
                    HttpForwardResponse.error(
                        forwardId,
                        response.getStatusCode(),
                        response.getBody().asString()
                    )
                );
            }

        } catch (Exception e) {
            log.error("Error forwarding message {}", forwardId, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                HttpForwardResponse.error(forwardId, 0, e.getMessage())
            );
        }
    }

    // Helper methods

    private ValidationResult validateRequest(HttpSendRequest request) {
        if (request.getTo() == null || request.getTo().isEmpty()) {
            return ValidationResult.invalid("Destination number is required");
        }
        if (request.getText() == null || request.getText().isEmpty()) {
            return ValidationResult.invalid("Message text is required");
        }
        if (request.getProvider() == null || request.getProvider().isEmpty()) {
            return ValidationResult.invalid("Provider is required");
        }
        return ValidationResult.valid();
    }

    private HttpProviderHandler getProviderHandler(String provider) {
        if (providerRegistry != null) {
            return providerRegistry.getHandler(provider);
        }
        // Return default handler
        return new DefaultHttpProviderHandler();
    }

    private CompletableFuture<HttpSendResult> processMessageAsync(
            HttpSendRequest request, HttpProviderHandler handler) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Apply provider-specific processing
                HttpSendResult result = handler.processMessage(request);

                // Simulate network delay
                if (request.getSimulateDelay() != null && request.getSimulateDelay() > 0) {
                    Thread.sleep(request.getSimulateDelay());
                }

                return result;

            } catch (Exception e) {
                log.error("Error processing message", e);
                return HttpSendResult.error(e.getMessage());
            }
        });
    }

    private void cacheHttpMessage(HttpSendRequest request, HttpSendResult result) {
        MessagesObject message = new MessagesObject();
        message.setId(result.getMessageId());
        message.setFrom(request.getFrom());
        message.setTo(request.getTo());
        message.setText(request.getText());
        message.setHttpMessage(request.toJson());
        message.setMessageTime(String.valueOf(System.currentTimeMillis()));
        message.setDir("OUT");
        message.setProviderId(request.getProvider());
        message.setImplementationType("HTTP");

        messagesCache.getMap().put(result.getMessageId(), message);
        messagesCache.setDirty(true);
    }

    private void cacheForwardedMessage(HttpForwardRequest request, Response response) {
        MessagesObject message = new MessagesObject();
        String messageId = UUID.randomUUID().toString();
        message.setId(messageId);
        message.setText(request.getPayload());
        message.setHttpMessage(request.toJson());
        message.setDirectResponse(response.getBody().asString());
        message.setMessageTime(String.valueOf(System.currentTimeMillis()));
        message.setDir("FORWARD");
        message.setImplementationType("HTTP");

        messagesCache.getMap().put(messageId, message);
        messagesCache.setDirty(true);
    }

    private void processDeliveryStatus(MessagesObject message, HttpDeliveryReceiptRequest receipt) {
        // Update message status based on receipt
        if ("delivered".equalsIgnoreCase(receipt.getStatus())) {
            message.setDeliveryReceiptShortMessage("DELIVERED");
        } else if ("failed".equalsIgnoreCase(receipt.getStatus())) {
            message.setDeliveryReceiptShortMessage("FAILED: " + receipt.getErrorCode());
        } else {
            message.setDeliveryReceiptShortMessage(receipt.getStatus().toUpperCase());
        }
    }

    private void updateProviderMetrics(String provider, boolean success) {
        String key = provider + "_" + (success ? "success" : "failure");
        providerMetrics.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    // Request/Response DTOs

    public static class HttpSendRequest {
        private String from;
        private String to;
        private String text;
        private String provider;
        private String encoding;
        private Map<String, String> metadata;
        private Long simulateDelay;

        // Getters and setters
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
        public Long getSimulateDelay() { return simulateDelay; }
        public void setSimulateDelay(Long simulateDelay) { this.simulateDelay = simulateDelay; }

        public String toJson() {
            // Simple JSON representation
            return String.format("{\"from\":\"%s\",\"to\":\"%s\",\"text\":\"%s\",\"provider\":\"%s\"}",
                from, to, text, provider);
        }
    }

    public static class HttpSendResponse {
        private String requestId;
        private String messageId;
        private boolean success;
        private String message;
        private long timestamp = System.currentTimeMillis();

        public static HttpSendResponse success(String requestId, String messageId, String message) {
            HttpSendResponse response = new HttpSendResponse();
            response.requestId = requestId;
            response.messageId = messageId;
            response.success = true;
            response.message = message;
            return response;
        }

        public static HttpSendResponse error(String requestId, String message) {
            HttpSendResponse response = new HttpSendResponse();
            response.requestId = requestId;
            response.success = false;
            response.message = message;
            return response;
        }

        // Getters and setters
        public String getRequestId() { return requestId; }
        public String getMessageId() { return messageId; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
    }

    public static class HttpDeliveryReceiptRequest {
        private String messageId;
        private String status;
        private String errorCode;
        private long deliveryTime;

        // Getters and setters
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        public long getDeliveryTime() { return deliveryTime; }
        public void setDeliveryTime(long deliveryTime) { this.deliveryTime = deliveryTime; }

        public String toJson() {
            return String.format("{\"messageId\":\"%s\",\"status\":\"%s\",\"errorCode\":\"%s\"}",
                messageId, status, errorCode);
        }
    }

    public static class HttpDeliveryReceiptResponse {
        private String receiptId;
        private boolean success;
        private String message;

        public static HttpDeliveryReceiptResponse success(String receiptId, String message) {
            HttpDeliveryReceiptResponse response = new HttpDeliveryReceiptResponse();
            response.receiptId = receiptId;
            response.success = true;
            response.message = message;
            return response;
        }

        public static HttpDeliveryReceiptResponse error(String receiptId, String message) {
            HttpDeliveryReceiptResponse response = new HttpDeliveryReceiptResponse();
            response.receiptId = receiptId;
            response.success = false;
            response.message = message;
            return response;
        }

        // Getters
        public String getReceiptId() { return receiptId; }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class HttpBulkSendRequest {
        private List<HttpSendRequest> messages;

        public List<HttpSendRequest> getMessages() { return messages; }
        public void setMessages(List<HttpSendRequest> messages) { this.messages = messages; }
    }

    public static class HttpBulkSendResponse {
        private String bulkId;
        private int total;
        private int successful;
        private int failed;
        private List<HttpSendResponse> results;

        public HttpBulkSendResponse(String bulkId, int total, int successful, int failed,
                                   List<HttpSendResponse> results) {
            this.bulkId = bulkId;
            this.total = total;
            this.successful = successful;
            this.failed = failed;
            this.results = results;
        }

        // Getters
        public String getBulkId() { return bulkId; }
        public int getTotal() { return total; }
        public int getSuccessful() { return successful; }
        public int getFailed() { return failed; }
        public List<HttpSendResponse> getResults() { return results; }
    }

    public static class HttpForwardRequest {
        private String targetUrl;
        private String contentType = MediaType.APPLICATION_JSON_VALUE;
        private Map<String, String> headers = new HashMap<>();
        private String payload;
        private String authType;
        private String username;
        private String password;
        private String token;
        private String apiKeyHeader;
        private String apiKey;

        // Getters and setters
        public String getTargetUrl() { return targetUrl; }
        public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
        public String getAuthType() { return authType; }
        public void setAuthType(String authType) { this.authType = authType; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getApiKeyHeader() { return apiKeyHeader; }
        public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String toJson() {
            return String.format("{\"targetUrl\":\"%s\",\"authType\":\"%s\"}", targetUrl, authType);
        }
    }

    public static class HttpForwardResponse {
        private String forwardId;
        private boolean success;
        private int statusCode;
        private String response;

        public static HttpForwardResponse success(String forwardId, int statusCode, String response) {
            HttpForwardResponse resp = new HttpForwardResponse();
            resp.forwardId = forwardId;
            resp.success = true;
            resp.statusCode = statusCode;
            resp.response = response;
            return resp;
        }

        public static HttpForwardResponse error(String forwardId, int statusCode, String response) {
            HttpForwardResponse resp = new HttpForwardResponse();
            resp.forwardId = forwardId;
            resp.success = false;
            resp.statusCode = statusCode;
            resp.response = response;
            return resp;
        }

        // Getters
        public String getForwardId() { return forwardId; }
        public boolean isSuccess() { return success; }
        public int getStatusCode() { return statusCode; }
        public String getResponse() { return response; }
    }

    public static class HttpStatusResponse {
        private String messageId;
        private String status;
        private String error;
        private MessagesObject messageDetails;

        public static HttpStatusResponse from(MessagesObject message) {
            HttpStatusResponse response = new HttpStatusResponse();
            response.messageId = message.getId();
            response.status = message.getDeliveryReceiptShortMessage() != null ? "delivered" : "pending";
            response.messageDetails = message;
            return response;
        }

        public static HttpStatusResponse notFound(String messageId) {
            HttpStatusResponse response = new HttpStatusResponse();
            response.messageId = messageId;
            response.status = "not_found";
            response.error = "Message not found";
            return response;
        }

        public static HttpStatusResponse error(String messageId, String error) {
            HttpStatusResponse response = new HttpStatusResponse();
            response.messageId = messageId;
            response.status = "error";
            response.error = error;
            return response;
        }

        // Getters
        public String getMessageId() { return messageId; }
        public String getStatus() { return status; }
        public String getError() { return error; }
        public MessagesObject getMessageDetails() { return messageDetails; }
    }

    public static class HttpMetrics {
        private long totalRequests;
        private long successfulRequests;
        private long failedRequests;
        private int activeSessions;
        private Map<String, Long> providerMetrics;

        // Getters and setters
        public long getTotalRequests() { return totalRequests; }
        public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
        public long getSuccessfulRequests() { return successfulRequests; }
        public void setSuccessfulRequests(long successfulRequests) { this.successfulRequests = successfulRequests; }
        public long getFailedRequests() { return failedRequests; }
        public void setFailedRequests(long failedRequests) { this.failedRequests = failedRequests; }
        public int getActiveSessions() { return activeSessions; }
        public void setActiveSessions(int activeSessions) { this.activeSessions = activeSessions; }
        public Map<String, Long> getProviderMetrics() { return providerMetrics; }
        public void setProviderMetrics(Map<String, Long> providerMetrics) { this.providerMetrics = providerMetrics; }
    }

    // Helper classes

    private static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }

    private static class HttpSession {
        private final String sessionId;
        private final long createdTime;
        private long lastActivityTime;

        public HttpSession(String sessionId) {
            this.sessionId = sessionId;
            this.createdTime = System.currentTimeMillis();
            this.lastActivityTime = createdTime;
        }

        public void updateActivity() {
            this.lastActivityTime = System.currentTimeMillis();
        }
    }

    public static class HttpSendResult {
        private final boolean success;
        private final String messageId;
        private final String errorMessage;

        private HttpSendResult(boolean success, String messageId, String errorMessage) {
            this.success = success;
            this.messageId = messageId;
            this.errorMessage = errorMessage;
        }

        public static HttpSendResult success(String messageId) {
            return new HttpSendResult(true, messageId, null);
        }

        public static HttpSendResult error(String errorMessage) {
            return new HttpSendResult(false, null, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public String getMessageId() { return messageId; }
        public String getErrorMessage() { return errorMessage; }
    }
}