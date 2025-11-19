package com.telemessage.simulators.common.conf.resources;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicResourceManager {

    private final ResourceLoader resourceLoader;
    private final Map<String, Resource> resourceMap = new ConcurrentHashMap<>();

    public DynamicResourceManager(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public void addResource(String path, String location) {
        Resource resource = resourceLoader.getResource(location);
        if (resource.exists()) {
            resourceMap.put(path, resource);
        } else {
            // Log or handle the case where the resource does not exist
            System.out.println("Resource not found at location: " + location);
        }
    }

    public void removeResource(String path) {
        resourceMap.remove(path);
    }

    public Resource resolveResource(String path) {
        return resourceMap.get(path);
    }
}
