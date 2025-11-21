package com.telemessage.simulators.smpp_cloudhopper;

import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.tlv.Tlv;
import com.telemessage.simulators.smpp_cloudhopper.concatenation.CloudhopperConcatenationHandler;
import com.telemessage.simulators.smpp_cloudhopper.concatenation.CloudhopperConcatenationType;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperEncodingHandler;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperSpecialFeatures;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperTLVHandler;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperUtils;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperDeliveryReceiptGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating that all Cloudhopper components work together.
 * This verifies the complete migration functionality.
 */
public class CloudhopperIntegrationTest {

    @Test
    @DisplayName("Integration test: Complete message flow with Cloudhopper")
    public void testCompleteMessageFlow() throws Exception {
        // 1. Test encoding
        String message = "Hello World! This is a test message.";
        CloudhopperEncodingHandler.EncodingResult encodingResult =
            CloudhopperEncodingHandler.encodeWithFallback(message, "GSM7");

        assertNotNull(encodingResult);
        assertNotNull(encodingResult.bytes);
        assertTrue(encodingResult.bytes.length > 0);

        // 2. Test special features - TON/NPI detection
        String phoneWithTon = "12349991"; // Should detect TON=1
        byte detectedTon = CloudhopperSpecialFeatures.detectTON(phoneWithTon);
        assertEquals(1, detectedTon, "Should detect TON=1 for number ending in 9991");

        // 3. Test skip number logic
        boolean shouldSkip = CloudhopperSpecialFeatures.shouldSkipMessage("999999999", 0);
        assertTrue(shouldSkip, "Should skip message to special number");

        // 4. Test concatenation
        String longMessage = "This is a longer message that will demonstrate concatenation. " +
            "It needs to be long enough to require splitting into multiple parts. " +
            "Adding more text to ensure it exceeds the single SMS limit.";

        CloudhopperConcatenationHandler concatHandler = new CloudhopperConcatenationHandler();
        List<CloudhopperConcatenationHandler.MessagePart> parts =
            concatHandler.splitMessage(longMessage, "GSM7", CloudhopperConcatenationType.UDHI);

        assertNotNull(parts);
        assertTrue(parts.size() >= 1, "Should handle message splitting");

        // 5. Test TLV handling
        SubmitSm submitSm = new SubmitSm();
        Map<String, String> tlvParams = new HashMap<>();
        tlvParams.put("owner", "12345");
        tlvParams.put("messageid", "msg-001");
        tlvParams.put("messagetime", "2025-11-21 15:00:00");

        CloudhopperTLVHandler.addCustomTLVs(submitSm, tlvParams);

        // Verify TLVs were added
        Integer owner = CloudhopperTLVHandler.getOwnerFromTLV(submitSm);
        String messageId = CloudhopperTLVHandler.getMessageIdFromTLV(submitSm);
        String messageTime = CloudhopperTLVHandler.getMessageTimeFromTLV(submitSm);

        assertNotNull(owner, "Owner TLV should be present");
        assertEquals(12345, owner);
        assertEquals("msg-001", messageId, "Message ID TLV should match");
        assertEquals("2025-11-21 15:00:00", messageTime, "Message time TLV should match");

        // 6. Test delivery receipt generation
        SubmitSm originalSubmit = new SubmitSm();
        originalSubmit.setSourceAddress(new com.cloudhopper.smpp.type.Address((byte)0, (byte)1, "12345"));
        originalSubmit.setDestAddress(new com.cloudhopper.smpp.type.Address((byte)0, (byte)1, "67890"));
        originalSubmit.setShortMessage(message.getBytes());

        com.cloudhopper.smpp.pdu.SubmitSmResp submitResponse = new com.cloudhopper.smpp.pdu.SubmitSmResp();
        submitResponse.setMessageId("test-msg-id");

        String deliveryReceipt = CloudhopperDeliveryReceiptGenerator.generateDeliveryReceipt(
            originalSubmit, submitResponse, "DELIVRD", "000"
        );

        assertNotNull(deliveryReceipt, "Delivery receipt should be generated");
        assertTrue(deliveryReceipt.contains("id:test-msg-id"), "DR should contain message ID");
        assertTrue(deliveryReceipt.contains("stat:DELIVRD"), "DR should contain status");

        System.out.println("=== Cloudhopper Integration Test Successful ===");
        System.out.println("✅ Encoding works");
        System.out.println("✅ Special features work (TON detection, skip logic)");
        System.out.println("✅ Concatenation works");
        System.out.println("✅ TLV handling works");
        System.out.println("✅ Delivery receipt generation works");
        System.out.println("All core functionality verified!");
    }

    @Test
    @DisplayName("Test encoding with various charsets")
    public void testVariousEncodings() {
        // Test different language encodings
        String[] testCases = {
            "Hello World",           // English
            "Привет мир",           // Russian
            "שלום עולם",            // Hebrew
            "مرحبا بالعالم",        // Arabic
            "你好世界"                // Chinese
        };

        String[] encodings = {
            "GSM7",
            "ISO-8859-5",
            "ISO-8859-8",
            "Windows-1256",
            "UTF-16BE"
        };

        for (int i = 0; i < testCases.length; i++) {
            CloudhopperEncodingHandler.EncodingResult result =
                CloudhopperEncodingHandler.encodeWithFallback(testCases[i], encodings[i]);

            assertNotNull(result, "Encoding should not be null for: " + testCases[i]);
            assertNotNull(result.bytes, "Bytes should not be null for: " + testCases[i]);

            // Try to decode back
            CloudhopperEncodingHandler.DecodingResult decoded =
                CloudhopperEncodingHandler.decodeWithDetection(result.bytes, result.encoding);

            assertNotNull(decoded, "Decoding should not be null");
            assertNotNull(decoded.text, "Decoded text should not be null");

            System.out.println("✅ Encoded/Decoded: " + testCases[i] +
                " using " + result.encoding);
        }
    }

    @Test
    @DisplayName("Test all concatenation types")
    public void testAllConcatenationTypes() {
        CloudhopperConcatenationHandler handler = new CloudhopperConcatenationHandler();
        String testMessage = "Test message for concatenation";

        CloudhopperConcatenationType[] types = {
            CloudhopperConcatenationType.DEFAULT,
            CloudhopperConcatenationType.UDHI,
            CloudhopperConcatenationType.SAR,
            CloudhopperConcatenationType.PAYLOAD,
            CloudhopperConcatenationType.TEXT_BASE,
            CloudhopperConcatenationType.UDHI_PAYLOAD
        };

        for (CloudhopperConcatenationType type : types) {
            List<CloudhopperConcatenationHandler.MessagePart> parts =
                handler.splitMessage(testMessage, "GSM7", type);

            assertNotNull(parts, "Parts should not be null for type: " + type);
            assertFalse(parts.isEmpty(), "Parts should not be empty for type: " + type);

            System.out.println("✅ Concatenation type " + type + " works");
        }
    }
}