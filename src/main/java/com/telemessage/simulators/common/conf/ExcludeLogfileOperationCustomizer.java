package com.telemessage.simulators.common.conf;

import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

@Configuration
public class ExcludeLogfileOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // Check if the operation is associated with the /logfile endpoint
        if ("/monitor/logfile".equals(operation.getOperationId())) {
            // Exclude the /logfile endpoint from being displayed in "Try it out" in Swagger UI
            operation.setDeprecated(true);
        }
        if ("/monitor/info".equals(operation.getOperationId())) {
            // Exclude the /logfile endpoint from being displayed in "Try it out" in Swagger UI
            operation.setDeprecated(true);
        }
        if ("/monitor/health".equals(operation.getOperationId())) {
            // Exclude the /logfile endpoint from being displayed in "Try it out" in Swagger UI
            operation.setDeprecated(true);
        }
        if ("/monitor/health/**".equals(operation.getOperationId())) {
            // Exclude the /logfile endpoint from being displayed in "Try it out" in Swagger UI
            operation.setDeprecated(true);
        }
        return operation;
    }
}
