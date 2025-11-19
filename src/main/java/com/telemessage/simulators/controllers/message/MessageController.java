package com.telemessage.simulators.controllers.message;

import com.telemessage.simulators.common.Utils;
import com.telemessage.simulators.smpp.SMPPRequest;
import com.telemessage.simulators.smpp.SimUtils;
import com.telemessage.qatools.error.ErrorTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

import com.telemessage.simulators.smpp.SMPPConnection;
import com.telemessage.simulators.smpp.SMPPConnection.BindType;
import com.logica.smpp.pdu.SubmitSM;
import com.logica.smpp.pdu.DeliverSM;
import com.logica.smpp.pdu.Request;
import com.logica.smpp.pdu.WrongLengthOfStringException;
import com.telemessage.simulators.smpp.SMPPSimulator;

import java.nio.charset.StandardCharsets;
import java.util.Random;

@Slf4j
@Controller
@RequestMapping("/")
//@Profile({"local"})
public class MessageController {

    MessagesCache cacheService;
    private final ErrorTracker errorTracker;

    @Autowired
    public MessageController(MessagesCache cacheService, ErrorTracker errorTracker) {
        this.cacheService = cacheService;
        this.errorTracker = errorTracker;
    }

    @Autowired
    private SMPPSimulator smppSimulator;

    @GetMapping("/messages")
    @Cacheable("messages")
    public String getAllMessages(Model model) {
        log.info("CacheService getAllMessages.");
        model.addAttribute("data", cacheService.getMap().values().isEmpty()? new ArrayList<>() : cacheService.getMap().values());
        return "pages/messages";
    }

    @DeleteMapping("/sim/deleteAllMessages")
    @ResponseBody
    public boolean deleteAllMessages() {
        try {
            log.info("CacheService deleteAllMessages data");
            return cacheService.clearCache();
        } catch (Exception e) {
            log.error("Error executing CacheService", e);
            errorTracker.captureError(
                "MessageController.deleteAllMessages",
                e,
                "delete-all-messages-failed",
                Map.of(
                    "operation", "delete_all_messages"
                )
            );
        }
        return false;
    }

    @GetMapping(value = "/sim/getMessagesByTextContains", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public List<MessagesObject> getMessagesByTextContains(@RequestParam String text) {
        try {
            log.info("CacheService getMessagesByTextContains data");
            return cacheService.getMessagesByText(text);
        } catch (Exception e) {
            log.error("Error executing CacheService", e);
            errorTracker.captureError(
                "MessageController.getMessagesByTextContains",
                e,
                "get-messages-by-text-failed",
                Map.of(
                    "operation", "get_messages_by_text",
                    "text", text
                )
            );
        }
        return null;
    }

    @GetMapping(value = "/sim/getMessagesByMid", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public List<MessagesObject> getMessagesByMid(@RequestParam String mid) {
        try {
            log.info("CacheService getMessagesByMid data");
            return cacheService.getMessagesByID(mid);
        } catch (Exception e) {
            log.error("Error executing CacheService", e);
            errorTracker.captureError(
                "MessageController.getMessagesByMid",
                e,
                "get-messages-by-mid-failed",
                Map.of(
                    "operation", "get_messages_by_mid",
                    "messageId", mid
                )
            );
        }
        return null;
    }

    /**
     * Retrieve raw binary message data for a specific message
     */
    @GetMapping(value = "/sim/getMessageRawData/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> getMessageRawData(@PathVariable String id) {
        try {
            log.info("Retrieving raw message data for ID: {}", id);
            MessagesObject message = cacheService.getMessageByID(id);

            if (message != null && message.getRawMessageBytes() != null) {
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"message_" + id + ".bin\"")
                        .body(message.getRawMessageBytes());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error retrieving raw message data", e);
            errorTracker.captureError(
                "MessageController.getRawMessageData",
                e,
                "get-raw-message-data-failed",
                Map.of(
                    "operation", "get_raw_message_data",
                    "messageId", id
                )
            );
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get detailed message information including encoding and binary data info
     */
    @GetMapping(value = "/sim/getMessageDetails/{id}", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<?> getMessageDetails(@PathVariable String id) {
        try {
            log.info("Getting detailed message info for ID: {}", id);
            MessagesObject message = cacheService.getMessageByID(id);

            if (message == null) {
                return ResponseEntity.notFound().build();
            }

            // Create a message details object with additional properties
            MessageDetailsDTO details = new MessageDetailsDTO();
            details.setMessage(message);
            details.setHasBinaryData(message.getRawMessageBytes() != null);
            details.setEncoding(Optional.ofNullable(message.getMessageEncoding()).orElse("Unknown"));

            if (message.getRawMessageBytes() != null) {
                details.setBinaryDataSize(message.getRawMessageBytes().length);
            }

            return ResponseEntity.ok(details);
        } catch (Exception e) {
            log.error("Error getting message details", e);
            errorTracker.captureError(
                "MessageController.getMessageDetails",
                e,
                "get-message-details-failed",
                Map.of(
                    "operation", "get_message_details",
                    "messageId", id
                )
            );
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Get messages grouped by concatenation reference number
     * This endpoint returns both single messages and grouped concatenated messages
     */
    @GetMapping(value = "/sim/messages/grouped-by-concat", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public List<GroupedMessageResponse> getMessagesGroupedByConcat() {
        try {
            log.info("Getting messages grouped by concatenation");
            log.info("Cache has {} messages", cacheService.getMap().size());
            List<GroupedMessageResponse> result = cacheService.getMessagesGroupedByConcat();
            log.info("Returning {} grouped message responses", result.size());
            return result;
        } catch (Exception e) {
            log.error("Error grouping messages by concatenation", e);
            errorTracker.captureError(
                "MessageController.getGroupedMessages",
                e,
                "group-messages-failed",
                Map.of(
                    "operation", "group_messages_by_concatenation"
                )
            );
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * message filtering by text type (SMPP/HTTP), recipient, and provider ID
     */
    @GetMapping(value = "/sim/messages/filter/by-text-type", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<List<MessagesObject>> getMessagesByTextType(
            @RequestParam(required = true) String textType,
            @RequestParam(required = false) String recipientType,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String pid) {

        try {
            log.info("Filtering messages by textType: {}, recipientType: {}, recipient: {}, pid: {}",
                    textType, recipientType, recipient, pid);

            List<MessagesObject> messages = new ArrayList<>(cacheService.getMap().values());

            // Apply text type filter
            if ("SMPP".equalsIgnoreCase(textType)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getSendMessageSM()) ||
                                StringUtils.isNotEmpty(msg.getDeliveryReceiptShortMessage()))
                        .collect(Collectors.toList());
            } else if ("HTTP".equalsIgnoreCase(textType)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getHttpMessage()) ||
                                StringUtils.isNotEmpty(msg.getDeliveryReceiptHttpMessage()))
                        .collect(Collectors.toList());
            } // "ANY" doesn't need filtering

            // Apply additional filters
            messages = applyAdditionalFilters(messages, recipientType, recipient, pid);

            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error filtering messages by text type", e);
            errorTracker.captureError(
                "MessageController.filterMessagesByTextType",
                e,
                "filter-messages-by-text-type-failed",
                Map.of(
                    "operation", "filter_by_text_type",
                    "textType", textType
                )
            );
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Filter messages by any text content, recipient and provider ID
     */
    @GetMapping(value = "/sim/messages/filter/by-content", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<List<MessagesObject>> getMessagesByContent(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String recipientType,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String pid) {

        try {
            log.info("Filtering messages by content: {}, recipientType: {}, recipient: {}, pid: {}",
                    text, recipientType, recipient, pid);

            List<MessagesObject> messages = new ArrayList<>(cacheService.getMap().values());

            // Apply text content filter if provided
            if (StringUtils.isNotEmpty(text)) {
                messages = messages.stream()
                        .filter(msg -> containsText(msg, text))
                        .collect(Collectors.toList());
            }

            // Apply additional filters
            messages = applyAdditionalFilters(messages, recipientType, recipient, pid);

            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error filtering messages by content", e);
            errorTracker.captureError(
                "MessageController.filterMessagesByContent",
                e,
                "filter-messages-by-content-failed",
                Map.of(
                    "operation", "filter_by_content",
                    "content", String.valueOf(text)
                )
            );
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Filter messages by message text only (not delivery receipts), recipient and provider ID
     */
    @GetMapping(value = "/sim/messages/filter/by-message-only", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<List<MessagesObject>> getMessagesByMessageOnly(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String recipientType,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String pid) {

        try {
            log.info("Filtering by message text only: {}, recipientType: {}, recipient: {}, pid: {}",
                    text, recipientType, recipient, pid);

            List<MessagesObject> messages = new ArrayList<>(cacheService.getMap().values());

            // Filter out delivery receipts first
            messages = messages.stream()
                    .filter(msg -> StringUtils.isEmpty(msg.getDeliveryReceiptShortMessage()) &&
                            StringUtils.isEmpty(msg.getDeliveryReceiptHttpMessage()))
                    .collect(Collectors.toList());

            // Apply text content filter if provided
            if (StringUtils.isNotEmpty(text)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getText()) &&
                                msg.getText().contains(text))
                        .collect(Collectors.toList());
            }

            // Apply additional filters
            messages = applyAdditionalFilters(messages, recipientType, recipient, pid);

            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error filtering messages by message text only", e);
            errorTracker.captureError(
                "MessageController.getMessagesByMessageOnly",
                e,
                "filter-messages-by-message-only-failed",
                Map.of(
                    "operation", "filter_by_message_only",
                    "text", String.valueOf(text)
                )
            );
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Filter messages by recipient and provider ID
     */
    @GetMapping(value = "/sim/messages/filter/by-recipient", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<List<MessagesObject>> getMessagesByRecipient(
            @RequestParam(required = true) String recipientType,
            @RequestParam(required = true) String recipient,
            @RequestParam(required = false) String pid) {

        try {
            log.info("Filtering messages by recipientType: {}, recipient: {}, pid: {}",
                    recipientType, recipient, pid);

            if (StringUtils.isEmpty(recipient) || StringUtils.isEmpty(recipientType)) {
                return ResponseEntity.badRequest().build();
            }

            List<MessagesObject> messages = new ArrayList<>(cacheService.getMap().values());

            // Apply recipient filters
            messages = applyAdditionalFilters(messages, recipientType, recipient, pid);

            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error filtering messages by recipient", e);
            errorTracker.captureError(
                "MessageController.getMessagesByRecipient",
                e,
                "filter-messages-by-recipient-failed",
                Map.of(
                    "operation", "filter_by_recipient",
                    "recipientType", recipientType,
                    "recipient", recipient
                )
            );
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * get messages by Multi search criteria
     */
    @GetMapping(value = "/sim/messages/advanced-search", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<List<MessagesObject>> advancedMessageSearch(
            @RequestParam(required = false) String messageText,
            @RequestParam(required = false) String httpText,
            @RequestParam(required = false) String smppText,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String directResponseText,
            @RequestParam(required = false) Boolean includeDeliveryReceipts,
            @RequestParam(required = false) String direction) {

        try {
            log.info("Advanced search with multiple criteria");

            List<MessagesObject> messages = new ArrayList<>(cacheService.getMap().values());

            // Filter by message ID if provided
            if (StringUtils.isNotEmpty(messageId)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getId()) &&
                                msg.getId().contains(messageId))
                        .collect(Collectors.toList());
            }

            // Filter by message text if provided
            if (StringUtils.isNotEmpty(messageText)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getText()) &&
                                msg.getText().contains(messageText))
                        .collect(Collectors.toList());
            }

            // Filter by HTTP text if provided
            if (StringUtils.isNotEmpty(httpText)) {
                messages = messages.stream()
                        .filter(msg -> (StringUtils.isNotEmpty(msg.getHttpMessage()) &&
                                msg.getHttpMessage().contains(httpText)) ||
                                (StringUtils.isNotEmpty(msg.getDeliveryReceiptHttpMessage()) &&
                                        msg.getDeliveryReceiptHttpMessage().contains(httpText)))
                        .collect(Collectors.toList());
            }

            // Filter by SMPP text if provided
            if (StringUtils.isNotEmpty(smppText)) {
                messages = messages.stream()
                        .filter(msg -> (StringUtils.isNotEmpty(msg.getSendMessageSM()) &&
                                msg.getSendMessageSM().contains(smppText)) ||
                                (StringUtils.isNotEmpty(msg.getDeliveryReceiptShortMessage()) &&
                                        msg.getDeliveryReceiptShortMessage().contains(smppText)))
                        .collect(Collectors.toList());
            }

            // Filter by source address
            if (StringUtils.isNotEmpty(source)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getFrom()) &&
                                msg.getFrom().contains(source))
                        .collect(Collectors.toList());
            }

            // Filter by destination address
            if (StringUtils.isNotEmpty(destination)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getTo()) &&
                                msg.getTo().contains(destination))
                        .collect(Collectors.toList());
            }

            // Filter by provider ID
            if (StringUtils.isNotEmpty(providerId)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getProviderId()) &&
                                msg.getProviderId().contains(providerId))
                        .collect(Collectors.toList());
            }

            // Filter by direction (In/Out)
            if (StringUtils.isNotEmpty(direction)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getDir()) &&
                                msg.getDir().equalsIgnoreCase(direction))
                        .collect(Collectors.toList());
            }

            // Filter out delivery receipts if needed
            if (includeDeliveryReceipts != null && !includeDeliveryReceipts) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isEmpty(msg.getDeliveryReceiptShortMessage()) &&
                                StringUtils.isEmpty(msg.getDeliveryReceiptHttpMessage()))
                        .collect(Collectors.toList());
            }

            // Filter by direct response text contains if needed
            if (StringUtils.isNotEmpty(directResponseText)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getDirectResponse()) &&
                                msg.getDirectResponse().contains(directResponseText))
                        .collect(Collectors.toList());
            }
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error in advanced message search", e);
            errorTracker.captureError(
                "MessageController.advancedMessageSearch",
                e,
                "advanced-message-search-failed",
                Map.of(
                    "operation", "advanced_message_search",
                    "messageId", String.valueOf(messageId)
                )
            );
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * delete by Multi search criteria
     */
    @DeleteMapping(value = "/sim/messages/deleteBy", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<List<MessagesObject>> messagesDeleteBy(
            @RequestParam(required = false) String messageText,
            @RequestParam(required = false) String httpText,
            @RequestParam(required = false) String smppText,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String directResponseText,
            @RequestParam(required = false) Boolean includeDeliveryReceipts,
            @RequestParam(required = false) String direction) {

        try {
            log.info("search and delete with multiple criteria");

            List<MessagesObject> messages = new ArrayList<>(cacheService.getMap().values());

            // Filter by message ID if provided
            if (StringUtils.isNotEmpty(messageId)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getId()) &&
                                msg.getId().contains(messageId))
                        .collect(Collectors.toList());
            }

            // Filter by message text if provided
            if (StringUtils.isNotEmpty(messageText)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getText()) &&
                                msg.getText().contains(messageText))
                        .collect(Collectors.toList());
            }

            // Filter by HTTP text if provided
            if (StringUtils.isNotEmpty(httpText)) {
                messages = messages.stream()
                        .filter(msg -> (StringUtils.isNotEmpty(msg.getHttpMessage()) &&
                                msg.getHttpMessage().contains(httpText)) ||
                                (StringUtils.isNotEmpty(msg.getDeliveryReceiptHttpMessage()) &&
                                        msg.getDeliveryReceiptHttpMessage().contains(httpText)))
                        .collect(Collectors.toList());
            }

            // Filter by SMPP text if provided
            if (StringUtils.isNotEmpty(smppText)) {
                messages = messages.stream()
                        .filter(msg -> (StringUtils.isNotEmpty(msg.getSendMessageSM()) &&
                                msg.getSendMessageSM().contains(smppText)) ||
                                (StringUtils.isNotEmpty(msg.getDeliveryReceiptShortMessage()) &&
                                        msg.getDeliveryReceiptShortMessage().contains(smppText)))
                        .collect(Collectors.toList());
            }

            // Filter by source address
            if (StringUtils.isNotEmpty(source)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getFrom()) &&
                                msg.getFrom().contains(source))
                        .collect(Collectors.toList());
            }

            // Filter by destination address
            if (StringUtils.isNotEmpty(destination)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getTo()) &&
                                msg.getTo().contains(destination))
                        .collect(Collectors.toList());
            }

            // Filter by provider ID
            if (StringUtils.isNotEmpty(providerId)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getProviderId()) &&
                                msg.getProviderId().contains(providerId))
                        .collect(Collectors.toList());
            }

            // Filter by direction (In/Out)
            if (StringUtils.isNotEmpty(direction)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getDir()) &&
                                msg.getDir().equalsIgnoreCase(direction))
                        .collect(Collectors.toList());
            }

            // Filter out delivery receipts if needed
            if (includeDeliveryReceipts != null && !includeDeliveryReceipts) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isEmpty(msg.getDeliveryReceiptShortMessage()) &&
                                StringUtils.isEmpty(msg.getDeliveryReceiptHttpMessage()))
                        .collect(Collectors.toList());
            }

            // Filter by direct response text contains if needed
            if (StringUtils.isNotEmpty(directResponseText)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getDirectResponse()) &&
                                msg.getDirectResponse().contains(directResponseText))
                        .collect(Collectors.toList());
            }
            
            // Delete all messages in batch
            messages.forEach(msg -> {
                cacheService.getMap().remove(msg.getId());
            });
            
            // Single write after all deletes
            if (!messages.isEmpty()) {
                cacheService.setDirty(true);
                log.info("Deleted {} messages", messages.size());
            }
            // return deleted messages
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error in advanced message search", e);
            errorTracker.captureError(
                "MessageController.messagesDeleteBy",
                e,
                "messages-delete-by-failed",
                Map.of(
                    "operation", "messages_delete_by",
                    "messageId", String.valueOf(messageId)
                )
            );
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Simulate receiving an SMPP message from outside by injecting it into a bound receiver.
     * Example POST: /sim/receiveSMPPMessage?bindId=1&from=123&to=456&text=hello
     */
    @PostMapping(value = "/sim/receiveSMPPMessage", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> receiveSMPPMessage(
            @RequestParam int bindId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam String text) {
        try {
            SMPPConnection receiver = smppSimulator.getReceiver(bindId);
            if (receiver == null) {
                receiver = smppSimulator.getTransceiver(bindId);
            }
            if (receiver == null) {
                return ResponseEntity.badRequest().body("Receiver or transceiver with bindId " + bindId + " not found or not bound.");
            }

            SMPPRequest.ConcatenationType concatType = receiver.getConcatenation();

            // Use prepareDataCodingAndEnc for encoding and dataCoding
            boolean isUnicode = !isGsm7(text);
            String enc = isUnicode ? "UTF-16BE" : "CCGSM";
            byte dataCoding = 0;
            org.apache.commons.lang3.tuple.Pair<Byte, String> codingAndEnc = SimUtils.prepareDataCodingAndEnc(isUnicode, enc, dataCoding, text);
            dataCoding = codingAndEnc.getLeft();
            enc = codingAndEnc.getRight();

            int maxLen = enc.equals("UTF-16BE") ? 70 : 160;
            int udhLen = 6;
            int partLen = (concatType == SMPPRequest.ConcatenationType.UDHI) ? maxLen - udhLen : maxLen;

            java.util.List<String> parts = Utils.splitByLength(text, partLen);
            int totalParts = parts.size();
            boolean isLong = totalParts > 1;
            int refNum = new Random().nextInt(255);
            int sentParts = 0;

            if (!isLong) {
                Request smppMsg;
                if (receiver.getBindType() == BindType.ESME) {
                    SubmitSM submitSM = new SubmitSM();
                    submitSM.setSourceAddr((byte)1, (byte)1, from);
                    submitSM.setDestAddr((byte)1, (byte)1, to);
                    submitSM.setShortMessage(text, enc);
                    submitSM.setDataCoding(dataCoding);
                    smppMsg = submitSM;
                } else {
                    DeliverSM deliverSM = new DeliverSM();
                    deliverSM.setSourceAddr((byte)1, (byte)1, from);
                    deliverSM.setDestAddr((byte)1, (byte)1, to);
                    deliverSM.setShortMessage(text, enc);
                    deliverSM.setDataCoding(dataCoding);
                    smppMsg = deliverSM;
                }
                receiver.receive(smppMsg);
                sentParts = 1;
            } else {
                for (int partNo = 1; partNo <= totalParts; partNo++) {
                    String partText = parts.get(partNo - 1);
                    Request smppMsg;
                    if (receiver.getBindType() == BindType.ESME) {
                        SubmitSM submitSM = new SubmitSM();
                        submitSM.setSourceAddr((byte)1, (byte)1, from);
                        submitSM.setDestAddr((byte)1, (byte)1, to);
                        submitSM.setDataCoding(dataCoding);
                        if (concatType == SMPPRequest.ConcatenationType.UDHI) {
                            submitSM.setEsmClass((byte)0x40);
                            byte[] udh = new byte[] {0x05, 0x00, 0x03, (byte)refNum, (byte)totalParts, (byte)partNo};
                            byte[] partBytes = enc.equals("UTF-16BE") ? partText.getBytes("UTF-16BE") : partText.getBytes(StandardCharsets.ISO_8859_1);
                            byte[] msg = new byte[udh.length + partBytes.length];
                            System.arraycopy(udh, 0, msg, 0, udh.length);
                            System.arraycopy(partBytes, 0, msg, udh.length, partBytes.length);
                            submitSM.setShortMessage(new String(msg, enc), enc);
                        } else if (concatType == SMPPRequest.ConcatenationType.SAR) {
                            submitSM.setSarMsgRefNum((short)refNum);
                            submitSM.setSarTotalSegments((short)totalParts);
                            submitSM.setSarSegmentSeqnum((short)partNo);
                            submitSM.setShortMessage(partText, enc);
                        } else if (concatType == SMPPRequest.ConcatenationType.PAYLOAD) {
                            byte[] partBytes = enc.equals("UTF-16BE") ? partText.getBytes("UTF-16BE") : partText.getBytes(StandardCharsets.ISO_8859_1);
                            com.logica.smpp.util.ByteBuffer payload = new com.logica.smpp.util.ByteBuffer(partBytes);
                            submitSM.setMessagePayload(payload);
                            submitSM.setShortMessage("", enc);
                        } else {
                            submitSM.setShortMessage(partText, enc);
                        }
                        smppMsg = submitSM;
                    } else {
                        DeliverSM deliverSM = new DeliverSM();
                        deliverSM.setSourceAddr((byte)1, (byte)1, from);
                        deliverSM.setDestAddr((byte)1, (byte)1, to);
                        deliverSM.setDataCoding(dataCoding);
                        if (concatType == SMPPRequest.ConcatenationType.UDHI) {
                            deliverSM.setEsmClass((byte)0x40);
                            byte[] udh = new byte[] {0x05, 0x00, 0x03, (byte)refNum, (byte)totalParts, (byte)partNo};
                            byte[] partBytes = enc.equals("UTF-16BE") ? partText.getBytes("UTF-16BE") : partText.getBytes(StandardCharsets.ISO_8859_1);
                            byte[] msg = new byte[udh.length + partBytes.length];
                            System.arraycopy(udh, 0, msg, 0, udh.length);
                            System.arraycopy(partBytes, 0, msg, udh.length, partBytes.length);
                            deliverSM.setShortMessage(new String(msg, enc), enc);
                        } else if (concatType == SMPPRequest.ConcatenationType.SAR) {
                            deliverSM.setSarMsgRefNum((short)refNum);
                            deliverSM.setSarTotalSegments((short)totalParts);
                            deliverSM.setSarSegmentSeqnum((short)partNo);
                            deliverSM.setShortMessage(partText, enc);
                        } else if (concatType == SMPPRequest.ConcatenationType.PAYLOAD) {
                            byte[] partBytes = enc.equals("UTF-16BE") ? partText.getBytes("UTF-16BE") : partText.getBytes(StandardCharsets.ISO_8859_1);
                            com.logica.smpp.util.ByteBuffer payload = new com.logica.smpp.util.ByteBuffer(partBytes);
                            deliverSM.setMessagePayload(payload);
                            deliverSM.setShortMessage("", enc);
                        } else {
                            deliverSM.setShortMessage(partText, enc);
                        }
                        smppMsg = deliverSM;
                    }
                    receiver.receive(smppMsg);
                    sentParts++;
                }
            }

            return ResponseEntity.ok("Message injected to receiver bindId=" + bindId +
                    " using concatType=" + concatType + ", parts=" + sentParts);
        } catch (WrongLengthOfStringException e) {
            log.error("Error building SMPP message", e);
            errorTracker.captureError(
                "MessageController.receiveSMPPMessage",
                e,
                "build-smpp-message-failed",
                Map.of(
                    "operation", "build_smpp_message",
                    "bindId", String.valueOf(bindId),
                    "from", from,
                    "to", to
                )
            );
            return ResponseEntity.badRequest().body("Error building SMPP message: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error injecting SMPP message", e);
            errorTracker.captureError(
                "MessageController.receiveSMPPMessage",
                e,
                "inject-smpp-message-failed",
                Map.of(
                    "operation", "inject_smpp_message",
                    "bindId", String.valueOf(bindId),
                    "from", from,
                    "to", to
                )
            );
            return ResponseEntity.badRequest().body("Error injecting SMPP message: " + e.getMessage());
        }
    }

    // Helper to check if text is GSM7
    private boolean isGsm7(String text) {
        for (char c : text.toCharArray()) {
            if (c > 127) return false;
        }
        return true;
    }

    /**
     * Helper method to apply recipient and provider ID filters
     */
    private List<MessagesObject> applyAdditionalFilters(
            List<MessagesObject> messages,
            String recipientType,
            String recipient,
            String pid) {

        // Apply recipient filter if provided
        if (StringUtils.isNotEmpty(recipientType) && StringUtils.isNotEmpty(recipient)) {
            if ("SRC".equalsIgnoreCase(recipientType)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getFrom()) &&
                                msg.getFrom().contains(recipient))
                        .collect(Collectors.toList());
            } else if ("DST".equalsIgnoreCase(recipientType)) {
                messages = messages.stream()
                        .filter(msg -> StringUtils.isNotEmpty(msg.getTo()) &&
                                msg.getTo().contains(recipient))
                        .collect(Collectors.toList());
            } else if ("BOTH".equalsIgnoreCase(recipientType)) {
                messages = messages.stream()
                        .filter(msg -> (StringUtils.isNotEmpty(msg.getFrom()) &&
                                msg.getFrom().contains(recipient)) ||
                                (StringUtils.isNotEmpty(msg.getTo()) &&
                                        msg.getTo().contains(recipient)))
                        .collect(Collectors.toList());
            }
        }

        // Apply provider ID filter if provided
        if (StringUtils.isNotEmpty(pid)) {
            messages = messages.stream()
                    .filter(msg -> StringUtils.isNotEmpty(msg.getProviderId()) &&
                            msg.getProviderId().contains(pid))
                    .collect(Collectors.toList());
        }

        return messages;
    }

    /**
     * Helper method to check if a message contains specific text in any field
     */
    private boolean containsText(MessagesObject msg, String text) {
        return (StringUtils.isNotEmpty(msg.getText()) && msg.getText().contains(text)) ||
                (StringUtils.isNotEmpty(msg.getSendMessageSM()) && msg.getSendMessageSM().contains(text)) ||
                (StringUtils.isNotEmpty(msg.getHttpMessage()) && msg.getHttpMessage().contains(text)) ||
                (StringUtils.isNotEmpty(msg.getDeliveryReceiptShortMessage()) &&
                        msg.getDeliveryReceiptShortMessage().contains(text)) ||
                (StringUtils.isNotEmpty(msg.getDeliveryReceiptHttpMessage()) &&
                        msg.getDeliveryReceiptHttpMessage().contains(text));
    }

    /**
     * Schedule wifi reconnect every 1h and set Cache obj
     */
    @Scheduled(cron = "0 0 */1 * * *") // Runs every 1 hour
    public void cleanupCacheOldRecordsScheduled() {
        long maxMapSize = 5000;
        long maxFileSize = 20971520 *2 ; //40M
//        long maxFileSize = 524288000 ; //500M
        // long maxFileSize = 524288000 ; //1G
        log.info("Running cleanupCacheOldRecords Scheduler every 1 hour. " +
                "if cache records size bigger then {} Or cache file bigger then {}", maxMapSize, maxFileSize);

        cacheService.cleanupCacheOldRecords(maxMapSize, maxFileSize);
    }
}
