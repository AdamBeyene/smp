package com.telemessage.simulators.common.conf.resources;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
//@EnableWebMvc
@Slf4j
public class ResourceConfiguration implements WebMvcConfigurer {

    private static final String[] CLASSPATH_RESOURCE_LOCATIONS = {
            "classpath:/META-INF/resources/", "classpath:/resources/",
            "classpath:/static/", "classpath:/public/" };




    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("shared/sim/allure/**")
                .addResourceLocations("file:/shared/sim/allure/")
                .addResourceLocations("file:shared/sim/allure/");

        registry.addResourceHandler("/pages/**")
                .addResourceLocations("classpath:/templates/pages/")
                .setCachePeriod(0); // Disable caching for development

        if (!registry.hasMappingForPattern("/css/**")) {
            log.debug("Adding resource handler for /css/**");
            registry.addResourceHandler("/css/**")
                    .addResourceLocations("classpath:/static/css/") // Map /css/** URLs to classpath:/static/css/
                    .setCachePeriod(3600); // Cache in production
        }

        if (!registry.hasMappingForPattern("/js/**")) {
            log.debug("Adding resource handler for /js/**");
            registry.addResourceHandler("/js/**")
                    .addResourceLocations("classpath:/static/js/") // Map /js/** URLs to classpath:/static/js/
                    .setCachePeriod(3600);
        }

        if (!registry.hasMappingForPattern("/images/**")) {
            log.debug("Adding resource handler for /images/**");
            registry.addResourceHandler("/images/**")
                    .addResourceLocations("classpath:/static/images/") // Map /images/** URLs to classpath:/static/images/
                    .setCachePeriod(3600);
        }

        if (!registry.hasMappingForPattern("/*.*")) { // Try mapping files with extensions in root
            log.debug("Adding resource handler for /*.* (root static files)");
            registry.addResourceHandler("/*.*")
                    .addResourceLocations("classpath:/static/", "classpath:/public/") // Look in static and public
                    .setCachePeriod(3600);
        }
    }


    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
//        registry.addRedirectViewController("/api/v3/api-docs", "/v3/api-docs");
//        registry.addRedirectViewController("/api/swagger-resources/configuration/ui", "/swagger-resources/configuration/ui");
//        registry.addRedirectViewController("/api/swagger-resources/configuration/security", "/swagger-resources/configuration/security");
        registry.addRedirectViewController("/swagger-resources", "/swagger-resources");



    }

}
