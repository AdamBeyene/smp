package com.telemessage.simulators.common.conf;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Map;

@Configuration
public class OpenApiBean {



    @Bean
    public OpenAPI springShopOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TELEMESSAGE SIM")
                        .description(
                                "TELEMESSAGE SIM For all smpp transactions with BE and KEEPER<br><br>" +
                                        "<p>&#128373; <b>MONITOR</b>: <a href='/server-details' target='_blank'>/server-details</a></p>" + /*"<br>" +*/
                                        "<p>\uD83D\uDD17 <b>Connections</b>: <a href='/connections' target='_blank'>/connections</a></p>" + /*"<br>" +*/
                                        "<p>\uD83D\uDCEC <b>Messages</b>: <a href='/messages' target='_blank'>/messages</a></p>" + /*"<br>" +*/
                                        "<p><b>\uD83C\uDF43✧ DB</b>: " +
                                        "<strong><a href='https://client-db-proxy.kapi.telemessage.com/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCClient DBProxy</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='https://smarsh-proxy-crnd.devops.telemessage.co.il/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCDEV DBProxy</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='https://smarsh-proxy-qacharlie.devops.telemessage.co.il/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCTEST DBProxy</a></strong>" +
                                        "<p><b>\uD83C\uDF43✧ KEEPER</b>: " +
                                        "<p><b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✧ HTTPSims</b>: " +
                                        "<strong><a href='https://sim-http2-dev.cm-dev.smarsh.us-west-2.aws.smarsh.cloud/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCDEV</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='https://sim-http2-test.cm-test.smarsh.us-west-2.aws.smarsh.cloud/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCTEST</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='https://sim-http2-uat.cm-uat.mt.us-east-1.aws.smarsh.cloud/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCUAT</a></strong>" +
                                        "</p>"+ /*"<br>" +*/
                                        "<p><b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✧ SMPPSims</b>: " +
                                        "<strong><a href='https://sim-smpp-dev.cm-dev.smarsh.us-west-2.aws.smarsh.cloud/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCDEV</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='https://sim-smpp-test.cm-test.smarsh.us-west-2.aws.smarsh.cloud/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCTEST</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='https://sim-smpp-uat.cm-uat.mt.us-east-1.aws.smarsh.cloud/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCUAT</a></strong>" +
                                        "</p>"+ /*"<br>" +*/
                                        "<p><b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✧ Maildev</b>: " +
                                        "<strong><a href='https://maildev-dev.cm-dev.smarsh.us-west-2.aws.smarsh.cloud' target='_blank'>\uD83D\uDCCCDEV</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='https://maildev-test.cm-test.smarsh.us-west-2.aws.smarsh.cloud' target='_blank'>\uD83D\uDCCCTEST</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='https://maildev-uat.cm-uat.mt.us-east-1.aws.smarsh.cloud' target='_blank'>\uD83D\uDCCCUAT</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='http://maildev-prod.telemessage.com:8080' target='_blank'>\uD83D\uDCCCPROD</a></strong>" +
                                        "</p>"+ /*"<br>" +*/
                                        "<p><b>\uD83C\uDF43✧ MASSMESS</b>: " +
                                        "<p><b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✧ HTTPSims</b>: " +
                                        "<strong><a href='http://crnd-sim.dev.telemessage.co.il:8032/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCDEV</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='http://qacharlie-sim.dev.telemessage.co.il:8032/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCTEST</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='http://qaarthur-sim.dev.telemessage.co.il:8032/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCArthur</a></strong>" +
//                                        "<strong><a href='https://sim-http2-uat.cm-uat.mt.us-east-1.aws.smarsh.cloud/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCUAT</a></strong>" +
                                        "</p>"+ /*"<br>" +*/
                                        "<p><b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✧ SMPPSims</b>: " +
                                        "<strong><a href='http://crnd-sim.dev.telemessage.co.il:8020/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCDEV</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='http://qacharlie-sim.dev.telemessage.co.il:8020/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCTEST</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='http://qaarthur-sim.dev.telemessage.co.il:8020/swagger-ui/index.html' target='_blank'>\uD83D\uDCCCArthur</a></strong>" +
                                        "</p>"+ /*"<br>" +*/
                                        "<p><b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;✧ Maildev</b>: " +
                                        "<strong><a href='https://maildev-crnd.devops.telemessage.co.il' target='_blank'>\uD83D\uDCCCDEV</a></strong>" +
                                        "<span>&nbsp;&nbsp;&nbsp;|&nbsp;&nbsp;&nbsp;</span>" +
                                        "<strong><a href='https://maildev-qacharlie.devops.telemessage.co.il' target='_blank' title=\"Click to open in new tab\">\uD83D\uDCCCTEST</a></strong>" +
                                        "</p>"+ /*"<br>" +*/
                                        "")
                        .version("v0.0.1")
                        .license(new License().name("TELEMESSAGE-SIM")
                                .url("")
                        )
                )
                .externalDocs(
                        new ExternalDocumentation()
                                .description("Sim Wiki Documentation")
                                .url("https://telemessage.sharepoint.com/sites/qa/qwiki/sitepages/http-simulators.aspx?web=1")
                )
                .addServersItem(new Server().url("/"));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public OpenApiCustomizer openApiCustomiser(Collection<Map.Entry<String, Example>> examples) {
        return openAPI -> {
            examples.forEach(example -> {
                openAPI.getComponents().addExamples(example.getKey(), example.getValue());
            });
        };
    }
    @Bean
    public OpenApiCustomizer actuatorOpenApiCustomiser() {
        return openApi -> openApi
                .path("/actuator/logfile", openApi.getPaths().get("/actuator/logfile"));
    }
//    @Bean
//    public OpenApiCustomizer actuatorOpenApiCustomiser() {
//        return openApi -> openApi
//                .path("/actuator/logfile", openApi.getPaths().get("/actuator/logfile"));
//    }

    public static String asString(Resource resource) {
        try (Reader reader = new InputStreamReader(resource.getInputStream())) {
            return FileCopyUtils.copyToString(reader);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

