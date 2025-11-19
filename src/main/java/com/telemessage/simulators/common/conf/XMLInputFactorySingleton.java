package com.telemessage.simulators.common.conf;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;

@Component
@Scope("singleton")
public class XMLInputFactorySingleton {

    private static final XMLInputFactory factory = XMLInputFactory.newInstance();

    private XMLInputFactorySingleton(){}

    public static XMLInputFactory getInstance() {
        return factory;
    }
}