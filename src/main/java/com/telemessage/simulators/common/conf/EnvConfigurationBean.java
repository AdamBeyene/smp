package com.telemessage.simulators.common.conf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

@Slf4j
@Component
@Configuration
@ComponentScan("com.telemessage.simulators")
public class EnvConfigurationBean {

    EnvConfiguration conf;
    String HOST = "";
    boolean isTmpDirCreated = false;

    @Autowired
    public EnvConfigurationBean(EnvConfiguration conf) {
        this.conf = conf;
    }

    @Bean(initMethod="fillSystemProperties" , name = "CONFIG")
    public EnvConfigurationBean initBean() {
        return new EnvConfigurationBean(conf);
    }

    public void fillSystemProperties()  {
        HOST = getHostAddress();

        //Handle custom dir
        conf.setBaseUrl(HOST);
        setSystemProperties();
        logSystemProperties();

        log.info("::::::::::::::::::::::::::::::::::::::::::::::::::::::" );
        log.info("::::::::::::::::::::::::::::::::::::::::::::::::::::::" );
        log.info("::::::::::::::::::::::::::::::::::::::::::::::::::::::" );
    }

    private void setSystemProperties() {
//        this.conf.getTMOBILE_BASIC_AUTH_PASSWORD();
        System.setProperty("local.shared.location", conf.getLocalSharedLocation());
        System.setProperty("env.url", conf.getEnvUrl());
        System.setProperty("env.name", conf.getEnvName());
    }

    private void logSystemProperties() {
        log.info("::::::::::::::::::::::::::::::::::::::::::::::::::::::");
        log.info(":::::::::::::::::::::::: Inits :::::::::::::::::::::::");
        log.info("::::::::::::::::::::::::::::::::::::::::::::::::::::::");
        log.info("::HOST = " + HOST );
        log.info("::app tmp , tomcat , logs at = '" + System.getProperty("java.io.tmpdir") + "' ~>isTmpDirCreated:" + isTmpDirCreated);
        log.info("::sim.smpp.active.conf =" + conf.getEnvCurrent());
        log.info("::env.name               =" + System.getProperty("env.name"));
        log.info("::env.host               =" + conf.getEnvHost());
        log.info("::local.shared.location        =" + System.getProperty("local.shared.location"));
        log.info("::sharedFolderName        =" + conf.getSharedFolderName());
        log.info("::env.url                =" + System.getProperty("env.url"));
        log.info("::RedisEnabled          =" + String.valueOf(conf.isRedisEnabled()));
        log.info("::RedisHost             =" + conf.getRedisHost());
        log.info("::RedisAuth              =" + conf.getRedisAuth());
        log.info("::smppWebPort            =" + String.valueOf(conf.getSmppWebPort()));
        log.info("::KeeperEnvHost          =" + conf.getEnvHost());
    }

    public static String getHostAddress() {
        String host = null;
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.isSiteLocalAddress()) {
                        host = inetAddress.getHostAddress();
                        break;
                    }
                }
                if (host != null) {
                    break;
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return host;
    }
}

