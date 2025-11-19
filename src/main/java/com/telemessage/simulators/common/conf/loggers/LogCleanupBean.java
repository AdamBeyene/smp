package com.telemessage.simulators.common.conf.loggers;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

@Slf4j
@Component
public class LogCleanupBean {
    private static Path LOG_DIR = Paths.get(System.getProperty("user.dir"))
            .resolve("shared").resolve("sim").resolve("logs").resolve("smpp");

    Path logFilePath = LOG_DIR.resolve("simApp.log");
    private final int numberOfDays = StringUtils.isEmpty(System.getProperty("daysToKeepLogs")) ?
            3 : Integer.parseInt(System.getProperty("daysToKeepLogs"));

//    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE_PATTERN = "simApp.%s.log";
    private static final int DAYS_TO_KEEP = 3;

//    @Scheduled(cron = "0 0 1 * * ?")  // Runs daily at 1 AM
  @Scheduled(cron = "0 */1 * * * *") // test Runs every 1 minute
    public void cleanLogFiles() {
      log.debug("cleanLogFiles cron: Runs daily at 1 AM ");
      if(!LOG_DIR.toFile().exists()) {
          LOG_DIR.toFile().mkdirs();
      }
      try (Stream<Path> stream = Files.list(LOG_DIR)) {
            stream
                    .filter(path -> path.toString().endsWith(".log"))
                    .filter(this::isOlderThanRetentionPeriod)
                    .forEach(this::deleteFile);
        } catch (IOException e) {
            e.printStackTrace();  // Replace with proper logging in real applications
        }
    }

    private boolean isOlderThanRetentionPeriod(Path path) {
        try {
            String fileName = path.getFileName().toString();
            String datePart = fileName.substring(fileName.indexOf('.') + 1, fileName.lastIndexOf('.'));
            LocalDateTime fileDate = LocalDateTime.parse(datePart, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return fileDate.isBefore(LocalDateTime.now().minusDays(DAYS_TO_KEEP));
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteFile(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            e.printStackTrace();  // Replace with proper logging in real applications
        }
    }


    private final Path springLogFilePath = LOG_DIR.resolve("simApp.log");  // Use your log file path

//    @Scheduled(cron = "0 0 0 * * ?")  // Run this daily at midnight
    @Scheduled(cron = "1 0 * * * ?")  // Every hour at minute 1
//    @Scheduled(cron = "0 * * * * ?")  // Run every minute
//    @Scheduled(cron = "*/20 * * * * ?")  // Every 20 seconds
    /**
     * Remove old logs
     * 1G max logfile
     * remove every 1h oldest 500MB
     */
    public void trimLogFile() throws IOException {
        log.info("Remove old logs from LogFile if exists.");
        File logFile = springLogFilePath.toFile();
        double maxSize = 1024 * 3; // 1G limit, for example

        if (logFile.exists() && logFile.length() > maxSize) {
            trimLogFile(springLogFilePath, maxSize);
        } else {
            log.info("Skipped Remove old logs from LogFile. file smaller then 1G");
        }
    }


    public static void trimLogFile(Path logFilePath, double maxSize) throws IOException {
        File logFile = logFilePath.toFile();

        // Check if the file exists and its size exceeds the max allowed size
        if (logFile.exists() && logFile.length() > maxSize) {
            // Calculate the number of bytes to keep (half the file size)
            long trimSize = logFile.length() / 2;

            // Read the file content
            try (RandomAccessFile file = new RandomAccessFile(logFile, "r")) {
                // Create a byte array to hold the content after the midpoint
                byte[] contentAfterTrim = new byte[(int) (logFile.length() - trimSize)];

                // Skip the first part (the oldest content)
                file.seek(trimSize);

                // Read the remaining content into the byte array
                file.readFully(contentAfterTrim);

                // Write the remaining content back to the file
                try (FileOutputStream out = new FileOutputStream(logFile)) {
                    out.write(contentAfterTrim);
                }
            }
        }
    }

}
