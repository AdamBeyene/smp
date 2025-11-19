package com.telemessage.simulators.common.services.filemanager;

import java.nio.file.Path;

public interface SimFileManagerInterface {
    void deleteFile(String filePath);

    void deleteFolder(Path folder);

    void deleteDirectoryRecursively(Path folder);

    void emptyFolder(Path folder);

    Path getSimRootFolder();

    Path getSharedFolder();
}