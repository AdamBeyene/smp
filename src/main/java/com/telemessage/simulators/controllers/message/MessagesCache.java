package com.telemessage.simulators.controllers.message;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemessage.qatools.error.ErrorTracker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Getter
@RequiredArgsConstructor
@Service
public class MessagesCache {

    private final ErrorTracker errorTracker;
    
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    final private String tempDir = System.getProperty("java.io.tmpdir");
    final private String cacheFileName = "Messages.json";
    
    // Dirty flag for batch writes
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    
    // Configuration for batch writes and cleanup (96h TTL as requested)
    private static final long FLUSH_INTERVAL_SECONDS = 5;
    private static final long CLEANUP_INTERVAL_HOURS = 1;
    private static final long MESSAGE_TTL_HOURS = 96;  // 96 hours as requested
    private static final int MAX_CACHE_SIZE = 100000;
    private static final int PARALLEL_THRESHOLD = 1000;
    
    // Encoding cache to avoid repeated charset lookups (performance optimization)
    private static final Map<String, java.nio.charset.Charset> ENCODING_CACHE = new ConcurrentHashMap<>();
    private static final java.nio.charset.Charset INVALID_ENCODING_MARKER = StandardCharsets.UTF_8;
    private static Path MessageFile_PATH = Paths.get(System.getProperty("user.dir"))
            .resolve("shared").resolve("sim").resolve("messages");
    final private Path WORKING_FILE = MessageFile_PATH.resolve(cacheFileName);
    private File file = null;
    private static ObjectMapper messageMapper = new ObjectMapper();
    private Map<String, MessagesObject> map;

    public void setDirty(boolean value) {
        dirty.set(value);
    }

    @PostConstruct
    public void init() {
        // Initialize map and load data
        map = new ConcurrentHashMap<>();
        try {
            readData();
        } catch (IOException e) {
            log.error("Failed to initialize cache from file", e);
            errorTracker.captureError(
                "MessagesCache.init",
                e,
                "cache-initialization-failed",
                Map.of(
                    "operation", "init",
                    "file", WORKING_FILE.toString()
                )
            );
        }

        // Start scheduled tasks
        // Periodic flush - writes cache every 5 seconds if dirty
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (dirty.getAndSet(false)) {
                    writeMapToJson(map);
                    log.debug("Periodic cache flush completed. Map size: {}", map.size());
                }
            } catch (Exception e) {
                log.error("Error during periodic cache flush", e);
                errorTracker.captureError(
                    "MessagesCache.periodicFlush",
                    e,
                    "periodic-cache-flush-failed",
                    Map.of(
                        "operation", "periodic_flush",
                        "mapSize", String.valueOf(map.size())
                    )
                );
                dirty.set(true);
            }
        }, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        
        // Periodic cleanup - removes old messages every hour
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredMessages();
            } catch (Exception e) {
                log.error("Error during periodic cleanup", e);
                errorTracker.captureError(
                    "MessagesCache.periodicCleanup",
                    e,
                    "periodic-cleanup-failed",
                    Map.of(
                        "operation", "periodic_cleanup",
                        "mapSize", String.valueOf(map.size())
                    )
                );
            }
        }, CLEANUP_INTERVAL_HOURS, CLEANUP_INTERVAL_HOURS, TimeUnit.HOURS);
        
        log.info("Started scheduled tasks: flush every {}s, cleanup every {}h, TTL={}h", 
            FLUSH_INTERVAL_SECONDS, CLEANUP_INTERVAL_HOURS, MESSAGE_TTL_HOURS);
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down cache service...");
        
        if (dirty.get()) {
            try {
                writeMapToJson(map);
                log.info("Final cache flush completed");
            } catch (Exception e) {
                log.error("Error during final cache flush", e);
                errorTracker.captureError(
                    "MessagesCache.finalFlush",
                    e,
                    "final-cache-flush-failed",
                    Map.of(
                        "operation", "final_flush",
                        "mapSize", String.valueOf(map.size()),
                        "dirtyFlag", String.valueOf(dirty.get())
                    )
                );
            }
        }
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Cache service shutdown complete");
    }
    
    private void cleanupExpiredMessages() {
        long cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(MESSAGE_TTL_HOURS);
        int initialSize = map.size();
        
        // Remove messages older than 96h TTL
        map.entrySet().removeIf(entry -> entry.getValue().getSimId() < cutoffTime);
        
        // Also enforce max size limit
        if (map.size() > MAX_CACHE_SIZE) {
            int toRemove = map.size() - MAX_CACHE_SIZE;
            List<String> oldestKeys = map.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().getSimId()))
                .limit(toRemove)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            oldestKeys.forEach(map::remove);
            log.warn("Removed {} messages to enforce max cache size of {}", toRemove, MAX_CACHE_SIZE);
        }
        
        int totalRemoved = initialSize - map.size();
        if (totalRemoved > 0) {
            log.info("Cleaned up {} expired messages (TTL: 96h). Remaining: {}", totalRemoved, map.size());
            dirty.set(true);
        }
    }

    public MessagesObject getMessageByID(String id) {
        return (map != null && !map.isEmpty()) ? map.get(id) : null;
    }

    private void readData() throws IOException {
        if(!MessageFile_PATH.toFile().exists()) {
            MessageFile_PATH.toFile().mkdirs();
        }
        file = WORKING_FILE.toFile();
        boolean fileExists = true;

        if (map==null || map.isEmpty()) {
            map = new ConcurrentHashMap<>();
        }
        if (!file.exists()) {
            fileExists = file.createNewFile();
        }

        if (fileExists) {
            try (BufferedReader reader = Files.newBufferedReader(WORKING_FILE, StandardCharsets.UTF_8)) {
                // Ignore unknown properties (safe for new params like messageEncoding)
                messageMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                // Only try to read if file is not empty
                if (file.length() > 0) {
                    try {
                        Map<String, MessagesObject> tempMap = messageMapper.readValue(reader,
                                new com.fasterxml.jackson.core.type.TypeReference<Map<String, MessagesObject>>() {});
                        if (tempMap != null) {
                            map.putAll(tempMap);
                            writeMapToJson(map);
                            log.info("Successfully loaded cache data from {}", WORKING_FILE);
                        }
                    } catch (IOException ex) {
                        log.error("Cache file is corrupted or contains invalid JSON. Initializing empty cache. Full exception:", ex);
                        errorTracker.captureError(
                            "MessagesCache.readData",
                            ex,
                            "cache-file-corrupted",
                            Map.of(
                                "operation", "read_cache_file",
                                "file", WORKING_FILE.toString(),
                                "issue", "corrupted_or_invalid_json"
                            )
                        );
                    }
                } else {
                    log.info("Cache file is empty. Initializing empty cache.");
                    map.clear();
                }
            } catch (IOException e) {
                log.warn("Failed to read cache file. The file may be corrupted or empty. Full exception:", e);
            }
        } else {
            log.warn("Cache file may have corrupted records or does not exist.");
        }
    }

    public boolean addCacheRecords(List<MessagesObject> objs) {
        return objs.stream()
                .allMatch(obj -> addCacheRecord(obj.getId(), obj));
    }

    public boolean addCacheRecord(String id, MessagesObject obj) {
        log.debug("Adding cache record for id {}", id);
        if (!file.exists()) {
            log.error("No Cache file exists.");
            errorTracker.captureError(
                "MessagesCache.addCacheRecord",
                new IOException("Cache file does not exist"),
                "cache-file-missing",
                Map.of(
                    "operation", "add_cache_record",
                    "messageId", id,
                    "file", file.getAbsolutePath()
                )
            );
            return false;
        }
        if (map == null) {
            map = new ConcurrentHashMap<>();
        }
        
        // Atomic compute operation - thread-safe
        map.compute(id, (key, current) -> {
            if (current != null) {
                log.info("Updated Message to cache: {}", id);
                return updateCacheRecord(current, obj);
            } else {
                log.info("Added new Message to cache: {}", id);
                return createNewMessageObject(obj);
            }
        });
        
        // Mark for batch write instead of immediate write
        dirty.set(true);
        return true;
    }

    public MessagesObject updateCacheRecord(MessagesObject current, MessagesObject obj) {
        log.debug("updateCacheRecord cache record for id {}", current.getId());
        // If the message already exists, update its fields.
        log.info("Updated Message to cache: {}", current.getId());
        updateMessageFields(current, obj);
        return current;
    }

    private void updateMessageFields(MessagesObject current, MessagesObject obj) {
//        if (StringUtils.isNotEmpty(obj.getDir())) {
//            log.debug("Updating message for {} dir: {}",obj.getId(), obj.getDir());
//            current.setDir(clean(obj.getDir()));
//        }
//        if (StringUtils.isNotEmpty(obj.getText())) {
//            log.debug("Updating message for {} text: {}",obj.getId(), obj.getText());
//            current.setText(clean(obj.getText()));
//        }
//        if (StringUtils.isNotEmpty(obj.getId())) {
//            log.debug("Updating message for {} id: {}",obj.getId(), obj.getId());
//            current.setId(clean(obj.getId()));
//        }
//        if (StringUtils.isNotEmpty(obj.getProviderId())) {
//            log.debug("Updating message for {} providerId: {}",obj.getId(), obj.getProviderId());
//            current.setProviderId(clean(obj.getProviderId()));
//        }
//        if (obj.getHttpMessage() != null) {
//            log.debug("Updating message for {} httpMessage: {}",obj.getId(), obj.getHttpMessage());
//            current.setHttpMessage(clean(obj.getHttpMessage()));
//        }
//        if (obj.getSendMessageSM() != null) {
//            log.debug("Updating message for {} sendMessageSM: {}",obj.getId(), obj.getSendMessageSM());
//            current.setSendMessageSM(clean(obj.getSendMessageSM()));
//        }
        if (obj.getDeliveryReceiptShortMessage() != null) {
            log.debug("Updating message for {} deliveryReceiptShortMessage: {}",obj.getId(), obj.getDeliveryReceiptShortMessage());
            current.setDeliveryReceiptShortMessage(clean(obj.getDeliveryReceiptShortMessage()));
        }
        if (obj.getDeliveryReceiptHttpMessage() != null) {
            log.debug("Updating message for {} deliveryReceiptHttpMessage: {}",obj.getId(), obj.getDeliveryReceiptHttpMessage());
            current.setDeliveryReceiptHttpMessage(clean(obj.getDeliveryReceiptHttpMessage()));
        }
        if (obj.getDirectResponse() != null) {
            log.debug("Updating message for {} directResponse: {}",obj.getId(), obj.getDirectResponse());
            String direct = current.getDirectResponse();
            current.setDirectResponse(direct + "\n" + clean(obj.getDirectResponse()));
        }
        if (obj.getDeliveryReceiptTime() != null) {
            log.debug("Updating message for {} deliveryReceiptTime: {}",obj.getId(), obj.getDeliveryReceiptTime());
            current.setDeliveryReceiptTime(clean(obj.getDeliveryReceiptTime()));
        } else {
            log.debug("Updating message for {} deliveryReceiptTime: {}",obj.getId(), MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()));
            current.setDeliveryReceiptTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()));
        }
//        // Ensure messageTime is set
//        if (obj.getMessageTime() != null && !obj.getMessageTime().isEmpty()) {
//            log.debug("Updating message for {} messageTime: {}",obj.getId(), obj.getMessageTime());
//            current.setMessageTime(clean(obj.getMessageTime()));
//        } else if (current.getMessageTime() == null || current.getMessageTime().isEmpty()) {
//            log.debug("Updating message for {} messageTime: {}",obj.getId(), MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()));
//            current.setMessageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()));
//        }
        if (obj.getRawMessageBytes() != null) {
            log.debug("Updating message for {} rawMessageBytes: {}",obj.getId(), obj.getRawMessageBytes());
            current.setRawMessageBytes(obj.getRawMessageBytes());
        }

    }

    private MessagesObject createNewMessageObject(MessagesObject obj) {
        // Ensure messageTime is set
        String messageTime = obj.getMessageTime();
        if (StringUtils.isEmpty(messageTime)) {
            messageTime = MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis());
        }

        // Validate and correct encoding if possible
        String validatedEncoding = validateAndCorrectEncoding(obj.getText(), obj.getMessageEncoding(), obj.getRawMessageBytes());

        return MessagesObject.builder()
                .simId(new Date().getTime())
                .text(clean(obj.getText()))
                .from(clean(obj.getFrom()))
                .to(clean(obj.getTo()))
                .dir(clean(obj.getDir()))
                .id(clean(obj.getId()))
                .providerId(clean(obj.getProviderId()))
                .sendMessageSM(clean(obj.getSendMessageSM()))
                .messageTime(messageTime)
                .deliveryReceiptShortMessage(clean(obj.getDeliveryReceiptShortMessage()))
                .httpMessage(clean(obj.getHttpMessage()))
                .deliveryReceiptHttpMessage(clean(obj.getDeliveryReceiptHttpMessage()))
                .deliveryReceiptTime(clean(obj.getDeliveryReceiptTime()))
                .directResponse(clean(obj.getDirectResponse()))
                .messageEncoding(validatedEncoding) // Use validated encoding
                .rawMessageBytes(obj.getRawMessageBytes()) // Preserve raw binary data if available
                .partNumber(obj.getPartNumber())
                .totalParts(obj.getTotalParts())
                .referenceNumber(obj.getReferenceNumber())
                .build();
    }

    private String clean(String s) {
        // Return the original string without filtering characters
        return s == null ? "" : s;
    }

    /**
     * Validates that the declared encoding matches the actual content
     * and attempts to correct if there's a mismatch
     *
     * @param text The decoded text content
     * @param declaredEncoding The encoding that was declared
     * @param rawBytes The raw bytes of the message (if available)
     * @return The validated (or corrected) encoding name
     */
    private String validateAndCorrectEncoding(String text, String declaredEncoding, byte[] rawBytes) {
        // If no encoding specified or no content, return as-is
        if (StringUtils.isEmpty(declaredEncoding) || StringUtils.isEmpty(text)) {
            return declaredEncoding != null ? declaredEncoding : "UNKNOWN";
        }

        // If no raw bytes available, we can't validate
        if (rawBytes == null || rawBytes.length == 0) {
            return declaredEncoding;
        }

        try {
            // Try to get the charset for the declared encoding
            java.nio.charset.Charset cs = getCharsetSafely(declaredEncoding);
            if (cs == null) {
                log.warn("Unsupported encoding '{}', marking as UNKNOWN", declaredEncoding);
                return "UNKNOWN";
            }

            // Decode the raw bytes with the declared encoding
            String decoded = new String(rawBytes, cs);

            // Compare with the actual text
            if (decoded.equals(text)) {
                // Encoding is correct
                return declaredEncoding;
            } else {
                // Mismatch detected - log warning
                log.warn("Encoding mismatch detected: declared='{}', content differs. Text length: {}, Decoded length: {}",
                        declaredEncoding, text.length(), decoded.length());

                // Try to detect the correct encoding
                String detectedEncoding = detectCorrectEncoding(text, rawBytes);
                if (detectedEncoding != null && !detectedEncoding.equals(declaredEncoding)) {
                    log.info("Corrected encoding from '{}' to '{}'", declaredEncoding, detectedEncoding);
                    return detectedEncoding;
                }

                // If we can't detect, keep the declared encoding but log the issue
                return declaredEncoding;
            }
        } catch (Exception e) {
            log.error("Failed to validate encoding '{}': {}", declaredEncoding, e.getMessage());
            errorTracker.captureError(
                "MessagesCache.detectCorrectEncoding",
                e,
                "encoding-validation-failed",
                Map.of(
                    "operation", "validate_encoding",
                    "declaredEncoding", declaredEncoding
                )
            );
            return declaredEncoding;
        }
    }

    /**
     * Attempts to detect the correct encoding by trying common encodings
     *
     * @param text The expected decoded text
     * @param rawBytes The raw bytes to decode
     * @return The detected encoding name, or null if unable to detect
     */
    private String detectCorrectEncoding(String text, byte[] rawBytes) {
        // List of common encodings to try, in order of likelihood
        String[] encodingsToTry = {
                "UTF-8",
                "UTF-16BE",
                "UTF-16LE",
                "ISO-8859-1",
                "US-ASCII",
                "Cp1252",
                "GSM7",
                "CCGSM",
                "SCGSM"
        };

        for (String encoding : encodingsToTry) {
            try {
                java.nio.charset.Charset cs = getCharsetSafely(encoding);
                if (cs != null) {
                    String decoded = new String(rawBytes, cs);
                    if (decoded.equals(text)) {
                        log.debug("Detected correct encoding: {}", encoding);
                        return encoding;
                    }
                }
            } catch (Exception e) {
                // Ignore and try next encoding
                log.trace("Failed to decode with {}: {}", encoding, e.getMessage());
            }
        }

        // Could not detect correct encoding
        log.warn("Unable to detect correct encoding for message. Text preview: {}",
                text.length() > 50 ? text.substring(0, 50) + "..." : text);
        return null;
    }

    /**
     * Safely gets a Charset, handling special cases like GSM7 encodings
     * Uses caching to avoid repeated lookups for the same encoding names
     *
     * @param encodingName The encoding name
     * @return The Charset, or null if not supported
     */
    private java.nio.charset.Charset getCharsetSafely(String encodingName) {
        if (encodingName == null) {
            return null;
        }

        // Check cache first - O(1) lookup
        java.nio.charset.Charset cached = ENCODING_CACHE.get(encodingName);
        if (cached != null) {
            // Return null if this encoding was previously determined to be invalid
            return cached == INVALID_ENCODING_MARKER ? null : cached;
        }

        // Not in cache, perform lookup and cache the result
        java.nio.charset.Charset result = lookupCharset(encodingName);
        
        // Cache the result (use marker for invalid encodings)
        ENCODING_CACHE.put(encodingName, result != null ? result : INVALID_ENCODING_MARKER);
        
        return result;
    }
    
    /**
     * Performs the actual charset lookup without caching
     *
     * @param encodingName The encoding name
     * @return The Charset, or null if not supported
     */
    private java.nio.charset.Charset lookupCharset(String encodingName) {
        try {
            // Handle GSM7 variants using the custom charset provider
            if ("GSM7".equalsIgnoreCase(encodingName) ||
                    "CCGSM".equalsIgnoreCase(encodingName) ||
                    "SCGSM".equalsIgnoreCase(encodingName)) {
                try {
                    return new com.telemessage.simulators.common.conf.CombinedCharsetProvider()
                            .charsetForName(encodingName);
                } catch (Exception e) {
                    log.trace("Failed to get GSM charset for {}, trying standard", encodingName);
                    // Fall back to ISO-8859-1 for GSM
                    return StandardCharsets.ISO_8859_1;
                }
            }

            // For standard encodings
            return java.nio.charset.Charset.forName(encodingName);
        } catch (Exception e) {
            log.trace("Charset '{}' not available: {}", encodingName, e.getMessage());
            return null;
        }
    }

    public boolean deleteMessageRecordById(String id) {
        MessagesObject removed = map.remove(id);
        if (removed != null) {
            log.info("MessagesObject cache removed: " + removed);
            dirty.set(true);
            return true;
        }
        return false;
    }

    public boolean writeMapToJson(Map<String, MessagesObject> map) {
        try (BufferedWriter writer = Files.newBufferedWriter(WORKING_FILE, StandardCharsets.UTF_8)) {
            messageMapper.writerWithDefaultPrettyPrinter().writeValue(writer, map);
            log.info("Successfully updated cache file.");
            return true;
        } catch (IOException e) {
            log.error("Failed to write cache data to file: {}", e.getMessage());
            errorTracker.captureError(
                "MessagesCache.writeMapToJson",
                e,
                "cache-write-failed",
                Map.of(
                    "operation", "write_cache_file",
                    "file", file.getAbsolutePath(),
                    "mapSize", String.valueOf(map.size())
                )
            );
            return false;
        }
    }

    /**
     * Cleanups
     *
     */

    public boolean clearCache() {
        map.clear();
        dirty.set(false);
        writeMapToJson(map);
        log.info("Cache cleared successfully. File: {}", file.getAbsolutePath());
        return true;
    }

    private static void clearJsonCacheFile(String filePath) {
        try (FileWriter fileWriter = new FileWriter(filePath)) {
            System.out.println("Json file content cleared: " + filePath);
        } catch (IOException e) {
            System.err.println("Failed to clear Json file content: " + e.getMessage());
            e.printStackTrace();
        }
    }

    void cleanupCacheOldRecords(long MAX_MAP_SIZE, long MAX_FILE_SIZE) {
        // Check if the map size exceeds the limit
        if (map.size() > MAX_MAP_SIZE) {
            log.info("Map size exceeds {} records. Cleaning up oldest records...", MAX_MAP_SIZE);
            cleanupOldRecords(MAX_MAP_SIZE, MAX_FILE_SIZE);
        }

        // Check if the file size exceeds the limit
        try {
            if (Files.exists(WORKING_FILE) && Files.size(WORKING_FILE) > MAX_FILE_SIZE) {
                log.info("Cache file size exceeds {} bytes. Cleaning up oldest records...", MAX_FILE_SIZE);
                cleanupOldRecords(MAX_MAP_SIZE, MAX_FILE_SIZE);
            }
        } catch (IOException e) {
            log.error("Failed to check file size: {}", e.getMessage());
            errorTracker.captureError(
                "MessagesCache.checkFileSize",
                e,
                "file-size-check-failed",
                Map.of(
                    "operation", "check_file_size",
                    "file", file.getAbsolutePath()
                )
            );
        }
    }


    private void cleanupOldRecords(long MAX_MAP_SIZE, long MAX_FILE_SIZE) {
        // If the map exceeds the max size, remove the oldest entries
        if (map.size() > MAX_MAP_SIZE) {
            int recordsToRemove = Math.toIntExact(map.size() - MAX_MAP_SIZE);
            log.info("Removing {} old records from map.", recordsToRemove);

            List<String> oldKeys = map.keySet().stream()
                    .sorted(Comparator.comparingLong(key -> map.get(key).getSimId()))
                    .limit(recordsToRemove)
                    .collect(Collectors.toList());

            oldKeys.forEach(map::remove);
            writeMapToJson(map);
        }

        // If the file exceeds the max size, remove the oldest records
        try {
            while(Files.exists(WORKING_FILE) && Files.size(WORKING_FILE) > MAX_FILE_SIZE/2) {
                List<String> keysToRemove = getKeysForCleanup();
                keysToRemove.forEach(map::remove);
                writeMapToJson(map);
            }
            log.info("Cache file cleanup completed. Removed records.");
        } catch (IOException e) {
            log.error("Failed to clean up the cache file: {}", e.getMessage());
            errorTracker.captureError(
                "MessagesCache.cleanupOldRecords",
                e,
                "cache-cleanup-failed",
                Map.of(
                    "operation", "cleanup_old_records",
                    "file", file.getAbsolutePath(),
                    "maxMapSize", String.valueOf(MAX_MAP_SIZE),
                    "maxFileSize", String.valueOf(MAX_FILE_SIZE)
                )
            );
        }
    }

    private List<String> getKeysForCleanup() {
        // For cleanup, prioritize older messages
        return map.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().getSimId()))
                .limit(150) // Adjust the number of records to be removed
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    /**
     * Get message paging
     *
     */




    /**
     * Get message
     *
     */
    public List<MessagesObject> getMessagesByID(String id) {
        log.debug("Retrieving messages by ID: {}", id);
        if (map == null || map.isEmpty()) {
            log.warn("Cache map is empty. No messages to search.");
            return Collections.emptyList();
        }
        
        Stream<MessagesObject> stream = map.size() > PARALLEL_THRESHOLD 
            ? map.values().parallelStream() 
            : map.values().stream();
        
        return stream
                .filter(message -> Objects.equals(message.getId(), id))
                .collect(Collectors.toList());
    }

    public List<MessagesObject> getMessagesByText(String searchText) {
        log.debug("Searching messages containing text: {}", searchText);
        if (map == null || map.isEmpty()) {
            log.warn("Cache map is empty. No messages to search.");
            return Collections.emptyList();
        }
        
        Stream<MessagesObject> stream = map.size() > PARALLEL_THRESHOLD 
            ? map.values().parallelStream() 
            : map.values().stream();
        
        return stream
                .filter(message -> contains(message.getText(), searchText)
                        || contains(message.getSendMessageSM(), searchText)
                        || contains(message.getHttpMessage(), searchText))
                .collect(Collectors.toList());
    }

    private boolean contains(String field, String searchText) {
        if (field == null || searchText == null) {
            return false;
        }
        return field.contains(searchText);
    }

    // Method to generate test data and save to file
    public Map<String, MessagesObject> generateTestData(int size, Path filePath) {
        Map<String, MessagesObject> testData = new ConcurrentHashMap<>();
        Random random = new Random();
        for (int i = 0; i < size; i++) {
            String uniqueId = String.valueOf(1000000000L + random.nextLong(1000000000L));
            String num = String.valueOf(1000000L + random.nextLong(1111111L));
            String numTo = String.valueOf(1000000L + random.nextLong(1111111L));
            String uniqueRandom = UUID.randomUUID().toString();
            long uniqueTimestamp = System.currentTimeMillis();
            String key = uniqueId;

            MessagesObject obj = MessagesObject.builder()
                    .simId(new Date().getTime())
                    .id(uniqueId)
                    .from("1781" + num)
                    .to("1781" + numTo)
                    .text(uniqueId + " " + uniqueRandom + " send @ test with at the  test with at the  test with at the  test with at the  test with at the  test with at the  test with at the . Sent at Tue Dec 10 08:22:34 IST 2024 " + uniqueTimestamp)
                    .sendMessageSM(uniqueId + " " +  generateRandomStringX(random.nextInt(1000) + 50))
                    .httpMessage(uniqueId + " " +  generateRandomStringX(random.nextInt(1000) + 50))
                    .directResponse(uniqueId + " " +  generateRandomStringX(random.nextInt(1000) + 50))
                    .providerId("prvdr_" + uniqueId)
                    .dir("IN")
                    // Add new fields for testing if desired:
                    .messageEncoding(i % 2 == 0 ? "GSM7" : "UTF-16BE")
                    // .rawMessageBytes(...) // if you want to test binary data
                    .build();

            testData.put(key, obj);
        }
        saveTestDataToFile(testData, filePath);
        return testData;
    }
    private static String generateRandomStringX(int length) {
        // Generate a random alphanumeric string of the desired length
        return RandomStringUtils.randomAlphanumeric(length);
    }

    private String generateRandomString(int length) {
//        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:',.<>?/~`";
        String chars = "START LONG TEXT {{current_date}} \\n1: Eng\\nabcdefghijklmnopqrstuvwxyz\\n2: Eng Caps\\naAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ\\n3: Hebrew\\nאבבּגדהוזחטיכךכּלמםנןסעפףפּצץקרששׁשׂת\\n4: Spanish\\n¡¿ÁÉÍÑÓÚÜáéíñóúü\\n5: Arabic\\nءآأؤإئابةتثجحخدذرزسشصضطظعغػؼؽؾؿـفقكلمنهوىي\\n6: Chinese\\n诶比西迪伊艾弗吉艾尺艾杰开艾勒艾马艾娜哦屁吉吾艾儿艾丝提伊吾维豆贝尔维艾克斯吾艾贼德\\n7: Japanese.1\\nあぁかさたなはまやゃらわがざだばぱいぃきしちにひみりゐぎじぢびぴうぅくすつぬふむゆゅるぐずづぶぷえ\\n7: Japanese.2\\nぇけせてねへめれゑげぜでべぺおぉこそとのほもよょろをごぞどぼぽゔっんーゝゞ、。\\n8: French\\nÀàÂâÆæÇçÉéÈèÊêËëÎîÏïÔôŒœÙùÛûÜüŸÿ«”“—»–’…·@¼½¾€\\n9: Russian.1\\nАаБбВвГгДдЕеЁёЖжЗзИиЙйКкЛлМмНнОоПпРрСсТтУуФфХх\\n9: Russian.2\\nЦцЧчШшЩщЪъЫыЬьЭэЮюЯяіІѣѢѳѲѵѴ\\n10: Currecny\\n$¢£€¥₹₽元¤₠₡₢₣₤₥₦₧₨₩₪₫₭₮₯₰₱₲₳₴₵₶₸₺₼₿৲৳૱௹฿៛㍐円圆圎圓圜원﷼＄￠￡￥￦\\n11: iso full\\nSP!\\\"#$%&'()*+,-.\\/0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{|}~NBSP¡¢£¤¥¦§¨©ª«¬SHY®¯°±²³´µ¶·¸¹º»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏĞÑÒÓÔÕÖ×ØÙÚÛÜİŞßàáâãäåæçèéêëìíîïğñòóôõö÷øùúûüışÿ\\n12: Emojis.1\\n\uD83D\uDE03\uD83D\uDE04\uD83D\uDE01\uD83D\uDE06\uD83D\uDE05\uD83E\uDD23\uD83D\uDE02\uD83D\uDE42\uD83D\uDE43\uD83D\uDE09\uD83D\uDE0A\uD83D\uDE07\uD83E\uDD70\uD83D\uDE0D\uD83E\uDD29\uD83D\uDE18\uD83D\uDE17☺\uFE0F\uD83D\uDE1A\uD83D\uDE19\uD83D\uDE0B\uD83D\uDE1B\uD83D\uDE1C\uD83E\uDD2A\uD83D\uDE1D\uD83E\uDD11\uD83E\uDD17\uD83E\uDD2D\uD83E\uDD2B\uD83E\uDD14\uD83E\uDD10\uD83E\uDD28\uD83D\uDE10\\n12: Emojis.2\\n\uD83D\uDC7B\uD83D\uDC7D\uD83D\uDC7E\uD83E\uDD16\uD83D\uDE3A\uD83D\uDE38\uD83D\uDE39\uD83D\uDE3B\uD83D\uDE3C\uD83D\uDE3D\uD83D\uDE40\uD83D\uDE3F\uD83D\uDE3E\uD83D\uDE48\uD83D\uDE49\uD83D\uDE4A\uD83D\uDC8B\uD83D\uDC8C\uD83D\uDC98\uD83D\uDC9D\uD83D\uDC96\uD83D\uDC97\uD83D\uDC93\uD83D\uDC9E\uD83D\uDC95\uD83D\uDC9F❣\uFE0F\uD83D\uDC94❤\uFE0F\u200D\uD83D\uDD25❤\uFE0F\u200D\uD83E\uDE79\\nFINAL END LONG TEXT. Sent at";
        StringBuilder sb = new StringBuilder(length);
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void saveTestDataToFile(Map<String, MessagesObject> data, Path filePath) {
        ObjectMapper objectMapper = new ObjectMapper();
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, data);
            log.info("Test data successfully saved to file: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to save test data to file: {}", e.getMessage());
            errorTracker.captureError(
                "MessagesCache.saveTestDataToFile",
                e,
                "test-data-save-failed",
                Map.of(
                    "operation", "save_test_data",
                    "filePath", filePath.toString()
                )
            );
        }
    }

    /**
     * Get all messages grouped by concatenation reference number
     * Returns a list of GroupedMessageResponse objects
     */
    public List<GroupedMessageResponse> getMessagesGroupedByConcat() {
        log.debug("Grouping messages by concatenation");
        if (map == null || map.isEmpty()) {
            log.warn("Cache map is empty. No messages to group.");
            return Collections.emptyList();
        }

        // Group messages by concatenation reference number
        Map<String, List<MessagesObject>> concatGroups = map.values().stream()
                .filter(msg -> msg.getReferenceNumber() != null && msg.getReferenceNumber() > 0)
                .collect(Collectors.groupingBy(msg ->
                    // Group key: referenceNumber + from + to to handle multiple conversations with same ref number
                    msg.getReferenceNumber() + "_" + msg.getFrom() + "_" + msg.getTo()
                ));

        List<GroupedMessageResponse> result = new ArrayList<>();

        // Process concat message groups
        for (Map.Entry<String, List<MessagesObject>> entry : concatGroups.entrySet()) {
            List<MessagesObject> parts = entry.getValue();

            if (parts.isEmpty()) continue;

            // Sort parts by part number
            parts.sort(Comparator.comparing(msg -> msg.getPartNumber() != null ? msg.getPartNumber() : 0));

            // Get the first part for reference
            MessagesObject firstPart = parts.get(0);
            Integer totalParts = firstPart.getTotalParts();
            Integer referenceNumber = firstPart.getReferenceNumber();
            int receivedParts = parts.size();
            boolean complete = totalParts != null && totalParts == receivedParts;

            // Assemble the full message text
            StringBuilder assembledText = new StringBuilder();
            for (MessagesObject part : parts) {
                if (part.getText() != null) {
                    assembledText.append(part.getText());
                }
            }

            // Create a full message object (based on first part)
            MessagesObject fullMessage = MessagesObject.builder()
                    .simId(firstPart.getSimId())
                    .id(firstPart.getId())
                    .providerId(firstPart.getProviderId())
                    .text(assembledText.toString())
                    .from(firstPart.getFrom())
                    .to(firstPart.getTo())
                    .dir(firstPart.getDir())
                    .sendMessageSM(firstPart.getSendMessageSM())
                    .messageTime(firstPart.getMessageTime())
                    .deliveryReceiptShortMessage(firstPart.getDeliveryReceiptShortMessage())
                    .httpMessage(firstPart.getHttpMessage())
                    .deliveryReceiptHttpMessage(firstPart.getDeliveryReceiptHttpMessage())
                    .deliveryReceiptTime(firstPart.getDeliveryReceiptTime())
                    .directResponse(firstPart.getDirectResponse())
                    .messageEncoding(firstPart.getMessageEncoding())
                    .referenceNumber(referenceNumber)
                    .totalParts(totalParts)
                    .partNumber(null) // Full message doesn't have a part number
                    .build();

            // Create metadata
            GroupedMessageResponse.ConcatMetadata metadata = GroupedMessageResponse.ConcatMetadata.builder()
                    .referenceNumber(referenceNumber)
                    .totalParts(totalParts)
                    .receivedParts(receivedParts)
                    .complete(complete)
                    .assembledText(assembledText.toString())
                    .build();

            // Create the grouped response
            GroupedMessageResponse response = GroupedMessageResponse.builder()
                    .type("concat")
                    .message(fullMessage)
                    .metadata(metadata)
                    .parts(parts.toArray(new MessagesObject[0]))
                    .build();

            result.add(response);
        }

        // Add single (non-concat) messages
        map.values().stream()
                .filter(msg -> msg.getReferenceNumber() == null || msg.getReferenceNumber() == 0)
                .forEach(msg -> {
                    GroupedMessageResponse response = GroupedMessageResponse.builder()
                            .type("single")
                            .message(msg)
                            .metadata(null)
                            .parts(null)
                            .build();
                    result.add(response);
                });

        // Sort by message time (most recent first)
        result.sort((a, b) -> {
            String timeA = a.getMessage().getMessageTime();
            String timeB = b.getMessage().getMessageTime();
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeB.compareTo(timeA);
        });

        log.info("Grouped {} total messages into {} groups ({} concat, {} single)",
                map.size(), result.size(),
                result.stream().filter(r -> "concat".equals(r.getType())).count(),
                result.stream().filter(r -> "single".equals(r.getType())).count());

        return result;
    }

}
