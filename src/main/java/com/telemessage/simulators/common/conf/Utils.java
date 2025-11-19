package com.telemessage.simulators.common.conf;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

@Slf4j
@Data
public class Utils {

        private static Path SHARED = Paths.get(System.getProperty("user.dir"))
                .resolve("shared");
        private static Path TMP = Paths.get(System.getProperty("user.dir"))
                .resolve("shared").resolve("sim").resolve("tmp");
        private static Path LOGS = Paths.get(System.getProperty("user.dir"))
                .resolve("shared").resolve("sim").resolve("logs").resolve("smpp");

        protected static boolean setApplicationDirectoriesAndLogs(Path tmpDir, int daysToKeepLogs){
            //LOGS Dir
            boolean isLogsDirExists = createOrCleanLogsFolder();
            // TMP DIR
            boolean isTmpDirExists = createOrCleanTmpDir(tmpDir);

            return isTmpDirExists && isLogsDirExists;
        }

    private static boolean createOrCleanTmpDir(Path tmpDir) {
        TMP = tmpDir != null ? tmpDir : TMP;
        boolean isTmpDirExists = TMP.toFile().exists();
        log.info("Set / clean Application TEMP Dir at: " + TMP);

        if(isTmpDirExists){
            log.info("Delete old tmp dir");
            try {
                Files.walk(TMP)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
//                            .filter(f -> !f.toString().endsWith(".zip"))
                        .forEach(File::delete);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(!TMP.toFile().exists())
            isTmpDirExists = TMP.toFile().mkdirs();

        if(isTmpDirExists) {
            System.setProperty("java.io.tmpdir" , TMP.toString());
        }
        return isTmpDirExists;
    }

    private static boolean createOrCleanLogsFolder() {
        boolean isLogsDirExists = LOGS.toFile().exists();
        log.info("Clean Application LOGS Dir at: " + LOGS);
        if(isLogsDirExists){
            log.info("Delete old tmp dir");
            try {
                Files.walk(LOGS)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .filter(f -> !f.getName().equals("simApp.log"))
                        .forEach(File::delete);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(!TMP.toFile().exists())
            isLogsDirExists = TMP.toFile().mkdirs();

        if(isLogsDirExists) {
            System.setProperty("java.io.tmpdir" , TMP.toString());
        }

        return isLogsDirExists;
    }

    public static boolean emptyDir(Path dir){

            boolean isTmpDirExists = dir.toFile().exists();
            final boolean[] success = {false};
            if(isTmpDirExists) {
                log.info("Empty old dir");
                try {
                    Files.walk(dir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(file -> success[0] = file.delete());


                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return success[0];
        }

}
