package com.telemessage.simulators.controllers.message;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public class MessageUtils {


    public static String getMessageDateFromTimestamp(long timestamp) {
        String date="";
        try {
            LocalDateTime dateTime;
            DateTimeFormatter formatter;
            dateTime = Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SS");
            date = dateTime.format(formatter);
        } catch (Exception e) {
            System.err.println("Error converting timestamp: " + e.getMessage());
            log.debug("Error converting timestamp: {}", timestamp);
            return "Invalid Timestamp";
        }
        log.debug("Converted timestamp: {} to: {}",timestamp, date);
        return date;
    }
}
