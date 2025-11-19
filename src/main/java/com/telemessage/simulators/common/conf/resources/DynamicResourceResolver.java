package com.telemessage.simulators.common.conf.resources;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import java.net.URLConnection;
import java.util.List;

@Slf4j
public class DynamicResourceResolver implements ResourceResolver {

    private final DynamicResourceManager resourceManager;

    public DynamicResourceResolver(DynamicResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Override
    public Resource resolveResource(HttpServletRequest request, String requestPath, List<? extends Resource> locations, ResourceResolverChain chain) {
        Resource resource = resourceManager.resolveResource(requestPath.replace("/*.html", "/**"));
        // Get the HttpServletResponse from the current request
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            HttpServletResponse response = requestAttributes.getResponse();
            if (response != null) {
                // Set content type based on file extension (e.g., HTML)
                String contentType = URLConnection.guessContentTypeFromName("*.html");
                if (contentType != null) {
                    response.setContentType(contentType);
                    response.setHeader("Content-Disposition", "inline");
                }
            }
            return resource;
        } else {
            // Optionally log or handle the case where the resource is not found
            log.error("resource not found");
            return null;
        }
    }

    @Override
    public String resolveUrlPath(String resourceUrlPath, List<? extends Resource> locations, ResourceResolverChain chain) {
        // Implement URL path resolution logic if needed
        return resourceUrlPath;
    }

}
