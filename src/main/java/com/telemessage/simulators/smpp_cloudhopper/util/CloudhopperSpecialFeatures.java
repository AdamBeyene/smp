package com.telemessage.simulators.smpp_cloudhopper.util;

import com.cloudhopper.smpp.type.Address;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Special features handler for Cloudhopper SMPP implementation.
 *
 * Implements all special behaviors from Logica:
 * - Phone pattern routing (TON/NPI detection by suffix)
 * - Skip number logic (999999999)
 * - Message part delays
 * - Alphanumeric sender detection
 * - Special routing rules
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-21
 */
@Slf4j
@Component
public class CloudhopperSpecialFeatures {

    // Special skip number - messages to this number are not sent
    public static final String SKIP_NUMBER = "999999999";
    public static final long SKIP_DELAY_VALUE = 999999999L;

    // Phone number patterns for TON detection
    private static final Pattern TON_1_PATTERN = Pattern.compile(".*9991$");  // Ends with 9991
    private static final Pattern TON_2_PATTERN = Pattern.compile(".*9992$");  // Ends with 9992
    private static final Pattern TON_3_PATTERN = Pattern.compile(".*9993$");  // Ends with 9993
    private static final Pattern TON_4_PATTERN = Pattern.compile(".*9994$");  // Ends with 9994
    private static final Pattern TON_5_PATTERN = Pattern.compile(".*9995$");  // Ends with 9995

    // Alphanumeric pattern (contains letters)
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile(".*[a-zA-Z].*");

    // International number pattern (starts with + or 00)
    private static final Pattern INTERNATIONAL_PATTERN = Pattern.compile("^(\\+|00)\\d+");

    /**
     * Checks if a message should be skipped.
     *
     * @param phoneNumber Destination phone number
     * @param delay Message delay value
     * @return true if message should be skipped
     */
    public static boolean shouldSkipMessage(String phoneNumber, long delay) {
        // Check skip number
        if (SKIP_NUMBER.equals(phoneNumber)) {
            log.info("Skipping message to special number: {}", SKIP_NUMBER);
            return true;
        }

        // Check skip delay value
        if (delay == SKIP_DELAY_VALUE) {
            log.info("Skipping message due to special delay value: {}", SKIP_DELAY_VALUE);
            return true;
        }

        return false;
    }

    /**
     * Applies message sending delay.
     *
     * @param delayMs Delay in milliseconds
     * @param partNumber Part number (for logging)
     * @param totalParts Total parts (for logging)
     * @return true if delay was applied, false if skipped
     */
    public static boolean applyMessageDelay(long delayMs, int partNumber, int totalParts) {
        if (delayMs <= 0) {
            return false;
        }

        if (delayMs == SKIP_DELAY_VALUE) {
            log.info("Skip delay detected for part {}/{}", partNumber, totalParts);
            return false; // Don't actually delay, this means skip
        }

        try {
            log.debug("Applying delay of {}ms for part {}/{}", delayMs, partNumber, totalParts);
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException e) {
            log.warn("Message delay interrupted for part {}/{}", partNumber, totalParts);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Detects TON (Type of Number) based on phone number pattern.
     *
     * Special suffixes:
     * - xxx9991 → TON=1 (International)
     * - xxx9992 → TON=2 (National)
     * - xxx9993 → TON=3 (Network specific)
     * - xxx9994 → TON=4 (Subscriber)
     * - xxx9995 → TON=5 (Alphanumeric)
     *
     * @param phoneNumber Phone number to analyze
     * @return TON byte value
     */
    public static byte detectTON(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return 0; // Unknown
        }

        // Check special suffixes
        if (TON_1_PATTERN.matcher(phoneNumber).matches()) {
            log.debug("TON=1 detected for number ending with 9991: {}", phoneNumber);
            return 1; // International
        }
        if (TON_2_PATTERN.matcher(phoneNumber).matches()) {
            log.debug("TON=2 detected for number ending with 9992: {}", phoneNumber);
            return 2; // National
        }
        if (TON_3_PATTERN.matcher(phoneNumber).matches()) {
            log.debug("TON=3 detected for number ending with 9993: {}", phoneNumber);
            return 3; // Network specific
        }
        if (TON_4_PATTERN.matcher(phoneNumber).matches()) {
            log.debug("TON=4 detected for number ending with 9994: {}", phoneNumber);
            return 4; // Subscriber
        }
        if (TON_5_PATTERN.matcher(phoneNumber).matches()) {
            log.debug("TON=5 detected for number ending with 9995: {}", phoneNumber);
            return 5; // Alphanumeric
        }

        // Check for alphanumeric sender
        if (ALPHANUMERIC_PATTERN.matcher(phoneNumber).matches()) {
            log.debug("TON=5 detected for alphanumeric number: {}", phoneNumber);
            return 5; // Alphanumeric
        }

        // Check for international format
        if (INTERNATIONAL_PATTERN.matcher(phoneNumber).matches()) {
            log.debug("TON=1 detected for international number: {}", phoneNumber);
            return 1; // International
        }

        // Default
        return 0; // Unknown
    }

    /**
     * Detects NPI (Numbering Plan Indicator) based on phone number and TON.
     *
     * @param phoneNumber Phone number to analyze
     * @param ton Type of Number
     * @return NPI byte value
     */
    public static byte detectNPI(String phoneNumber, byte ton) {
        // Alphanumeric TON always uses NPI=0
        if (ton == 5) {
            return 0; // Unknown
        }

        // E.164 international format
        if (ton == 1) {
            return 1; // ISDN/E.164
        }

        // Default to ISDN
        return 1; // ISDN/E.164
    }

    /**
     * Creates an Address object with automatic TON/NPI detection.
     *
     * @param phoneNumber Phone number
     * @return Configured Address object
     */
    public static Address createAddressWithDetection(String phoneNumber) {
        byte ton = detectTON(phoneNumber);
        byte npi = detectNPI(phoneNumber, ton);

        // Clean the phone number for special patterns
        String cleanedNumber = cleanPhoneNumber(phoneNumber, ton);

        return new Address(ton, npi, cleanedNumber);
    }

    /**
     * Cleans phone number based on TON.
     * Removes special suffixes used for TON detection.
     *
     * @param phoneNumber Original phone number
     * @param ton Detected TON
     * @return Cleaned phone number
     */
    private static String cleanPhoneNumber(String phoneNumber, byte ton) {
        if (phoneNumber == null) {
            return "";
        }

        // Remove special TON detection suffixes
        if (phoneNumber.endsWith("9991") ||
            phoneNumber.endsWith("9992") ||
            phoneNumber.endsWith("9993") ||
            phoneNumber.endsWith("9994") ||
            phoneNumber.endsWith("9995")) {

            // Only remove if it was used for TON detection
            if (ton >= 1 && ton <= 5) {
                String cleaned = phoneNumber.substring(0, phoneNumber.length() - 4);
                log.debug("Cleaned phone number: {} -> {}", phoneNumber, cleaned);
                return cleaned;
            }
        }

        return phoneNumber;
    }

    /**
     * Checks if an address is alphanumeric.
     *
     * @param address Address to check
     * @return true if alphanumeric
     */
    public static boolean isAlphanumeric(Address address) {
        if (address == null) {
            return false;
        }

        // Check TON
        if (address.getTon() == 5) {
            return true;
        }

        // Check address content
        String addr = address.getAddress();
        return addr != null && ALPHANUMERIC_PATTERN.matcher(addr).matches();
    }

    /**
     * Determines if special routing should be applied.
     *
     * @param source Source address
     * @param destination Destination address
     * @return true if special routing needed
     */
    public static boolean needsSpecialRouting(Address source, Address destination) {
        // Check for alphanumeric sender
        if (isAlphanumeric(source)) {
            log.debug("Special routing for alphanumeric sender");
            return true;
        }

        // Check for specific destination patterns
        if (destination != null && destination.getAddress() != null) {
            String dest = destination.getAddress();

            // Premium numbers
            if (dest.startsWith("1900") || dest.startsWith("0900")) {
                log.debug("Special routing for premium number: {}", dest);
                return true;
            }

            // Short codes
            if (dest.length() <= 6 && dest.matches("\\d+")) {
                log.debug("Special routing for short code: {}", dest);
                return true;
            }
        }

        return false;
    }

    /**
     * Validates a phone number.
     *
     * @param phoneNumber Phone number to validate
     * @return true if valid
     */
    public static boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return false;
        }

        // Skip number is technically valid (for testing)
        if (SKIP_NUMBER.equals(phoneNumber)) {
            return true;
        }

        // Alphanumeric is valid
        if (ALPHANUMERIC_PATTERN.matcher(phoneNumber).matches()) {
            return true;
        }

        // Must be numeric (with optional + prefix)
        return phoneNumber.matches("^\\+?\\d+$");
    }

    /**
     * Gets delay configuration for a message part.
     * Can be configured per part with different delays.
     *
     * @param partNumber Part number
     * @param defaultDelay Default delay in ms
     * @param partDelays Array of per-part delays (optional)
     * @return Delay in milliseconds for this part
     */
    public static long getPartDelay(int partNumber, long defaultDelay, long[] partDelays) {
        // Check for per-part configuration
        if (partDelays != null && partNumber > 0 && partNumber <= partDelays.length) {
            long partDelay = partDelays[partNumber - 1];
            if (partDelay > 0) {
                return partDelay;
            }
        }

        // Use default delay
        return defaultDelay;
    }

    /**
     * Formats a phone number for display.
     *
     * @param phoneNumber Phone number
     * @param ton Type of Number
     * @return Formatted phone number
     */
    public static String formatPhoneNumber(String phoneNumber, byte ton) {
        if (phoneNumber == null) {
            return "";
        }

        // Alphanumeric - no formatting
        if (ton == 5) {
            return phoneNumber;
        }

        // International - ensure + prefix
        if (ton == 1 && !phoneNumber.startsWith("+")) {
            return "+" + phoneNumber;
        }

        return phoneNumber;
    }

    /**
     * Configuration class for special features.
     */
    public static class SpecialFeaturesConfig {
        private boolean enablePhonePatternRouting = true;
        private boolean enableSkipLogic = true;
        private boolean enableMessageDelays = true;
        private boolean enableAlphanumericDetection = true;
        private long defaultMessageDelay = 0;
        private long[] partDelays = null;

        // Getters and setters
        public boolean isEnablePhonePatternRouting() {
            return enablePhonePatternRouting;
        }

        public void setEnablePhonePatternRouting(boolean enablePhonePatternRouting) {
            this.enablePhonePatternRouting = enablePhonePatternRouting;
        }

        public boolean isEnableSkipLogic() {
            return enableSkipLogic;
        }

        public void setEnableSkipLogic(boolean enableSkipLogic) {
            this.enableSkipLogic = enableSkipLogic;
        }

        public boolean isEnableMessageDelays() {
            return enableMessageDelays;
        }

        public void setEnableMessageDelays(boolean enableMessageDelays) {
            this.enableMessageDelays = enableMessageDelays;
        }

        public boolean isEnableAlphanumericDetection() {
            return enableAlphanumericDetection;
        }

        public void setEnableAlphanumericDetection(boolean enableAlphanumericDetection) {
            this.enableAlphanumericDetection = enableAlphanumericDetection;
        }

        public long getDefaultMessageDelay() {
            return defaultMessageDelay;
        }

        public void setDefaultMessageDelay(long defaultMessageDelay) {
            this.defaultMessageDelay = defaultMessageDelay;
        }

        public long[] getPartDelays() {
            return partDelays;
        }

        public void setPartDelays(long[] partDelays) {
            this.partDelays = partDelays;
        }
    }
}