package com.telemessage.simulators;

import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.common.services.filemanager.SimFileManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Slf4j
@Component
public class EnvUtils {

    private static EnvConfiguration conf;

    @Autowired
    public EnvUtils(EnvConfiguration conf) {
        this.conf = conf;
    }

    @Autowired
    private Environment environment;

    public static void fillSystemProperties() throws IOException {

        String env = conf.getEnvCurrent();
        log.info("Using environment: {}", env);
        Path filPath = Paths.get(StringUtils.isEmpty(env) ? "" : env).resolve("conf.properties");
        Properties myProps = new Properties();
        log.info("conf.properties path: {}", filPath);
        InputStream inputStream = SimFileManager.getResolvedResourcePath(filPath.toString());
        myProps.load(inputStream);
        for(String name : myProps.stringPropertyNames()) {
            System.setProperty(name, myProps.getProperty(name));
        }
    }

    public static String toHex(String arg) {
        return String.format("%x", new BigInteger(1, arg.getBytes(StandardCharsets.UTF_8)));
    }
}
