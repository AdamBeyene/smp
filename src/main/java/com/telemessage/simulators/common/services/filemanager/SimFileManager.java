package com.telemessage.simulators.common.services.filemanager;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@Primary
public class SimFileManager implements SimFileManagerInterface {

    private final Path BASE_DIR;


    public static InputStream getResolvedResourcePath(String resourcePath) {
        // Normalize the path to use "/" as a separator
        resourcePath = resourcePath.replace("\\", "/");

        // Ensure the path starts from the root of the classpath
        String classpathResource = "/com/telemessage/simulators/" + resourcePath;
        System.out.println("Attempting to load classpath resource: " + classpathResource);

        // Try to load the resource from the classpath
        InputStream returnInputStream = SimFileManager.class.getResourceAsStream(classpathResource);

        if (returnInputStream == null) {
            System.out.println("Classpath resource not found. Checking filesystem path: " + resourcePath);

            // Check the filesystem as a fallback
            Path filePath = Paths.get(resourcePath).toAbsolutePath();
            System.out.println("Attempting to load filesystem resource: " + filePath);

            if (Files.exists(filePath)) {
                try {
                    return Files.newInputStream(filePath);
                } catch (IOException e) {
                    throw new RuntimeException("Error reading filesystem resource: " + filePath, e);
                }
            } else {
                throw new RuntimeException("Resource not found at classpath or filesystem: " + classpathResource + " | " + filePath);
            }
        }

        return returnInputStream;
    }

    public SimFileManager() {
        this.BASE_DIR = StringUtils.isEmpty(System.getProperty("local.shared.location"))
                || System.getProperty("local.shared.location").equals("false")
                ? Paths.get("/shared") : Paths.get(System.getProperty("user.dir")).resolve("shared")
        ;
    }


    @Override
    public void deleteFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + filePath, e);
        }
    }


    @Override
    public void emptyFolder(Path folder) {
        try {
            Files.walk(folder)
                    .filter(path -> !path.equals(folder))  // Skip the root folder itself
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException("Failed to empty folder: " + folder.toFile().getName(), e);
        }
    }

    @Override
    public void deleteFolder(Path folder) {
        try {
            Files.walk(folder)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete folder: " + folder.toFile().getName(), e);
        }
    }

    public void deleteDirectoryRecursively(Path path) {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()) // files delet before directories
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.error("Failed to delete: {} - {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to walk the directory: {} - {}", path, e.getMessage());
        }
    }

    @Override
    public Path getSimRootFolder() {
        return BASE_DIR.resolve("sim");
    }

    @Override
    public Path getSharedFolder() {
        return BASE_DIR;
    }

    // Method to ensure only valid folders remain in the root directory
    public void cleanUpRootFolder() {
        Set<String> allowedFolders = new HashSet<>();

        // List all directories in the root folder
        try (Stream<Path> stream = Files.list(getSimRootFolder())) {
            stream
                    .filter(Files::isDirectory)  // Filter to get only directories
                    .forEach(folder -> {
                        String folderName = folder.getFileName().toString();
                        // If the folder is not in the allowed folders, delete it
                        if (!allowedFolders.contains(folderName)) {
                            deleteDirectoryRecursively(folder);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to walk the directory: {} - {}", getSimRootFolder(), e.getMessage());
        }
    }

}
