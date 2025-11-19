# Using Spring MCP Framework as a Dependency - Project B Configuration Guide

## Overview

This guide provides complete instructions for integrating the **Spring MCP Framework** (`qa-tools-mcp`) into your Spring Boot project (Project B). After following this guide, your project will have:

- ✅ Full MCP (Model Context Protocol) server capabilities
- ✅ Built-in error tracking and metrics collection
- ✅ Web-based monitoring dashboard at `/server-details`
- ✅ Dynamic logger management
- ✅ Automatic tool discovery and registration
- ✅ Swagger/OpenAPI documentation
- ✅ Real-time log streaming

**Integration Time:** ~5-10 minutes

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Step 1: Add Maven Dependency](#step-1-add-maven-dependency)
3. [Step 2: Configure JFrog Repository](#step-2-configure-jfrog-repository)
4. [Step 3: Enable MCP Server](#step-3-enable-mcp-server)
5. [Step 4: Configure Application](#step-4-configure-application)
6. [Step 5: Create Custom Tools](#step-5-create-custom-tools-optional)
7. [Step 6: Use Framework Components](#step-6-use-framework-components)
8. [Step 7: Configure .mcp.json](#step-7-configure-mcpjson-for-ide)
9. [Testing and Verification](#testing-and-verification)
10. [Best Practices and Common Pitfalls](#best-practices-and-common-pitfalls)
11. [Troubleshooting](#troubleshooting)
12. [Advanced Configuration](#advanced-configuration)

---

## Prerequisites

### Required
- **Java**: 21 or higher
- **Spring Boot**: 3.3.7 or higher
- **Maven**: 3.9.0 or higher (or Gradle equivalent)
- **Spring AI**: 1.1.0-M3 (managed by framework)

### Optional (for Web UI)
- `spring-boot-starter-web` - For REST endpoints and web dashboard
- `spring-boot-starter-thymeleaf` - For server details UI templates

> **Note**: The MCP server and tools work without web dependencies. The web UI is optional.

---

## Step 1: Add Maven Dependency

Add the Spring MCP Framework dependency to your `pom.xml`:

```xml
<dependencies>
    <!-- Spring MCP Framework -->
    <dependency>
        <groupId>com.telemessage.qa</groupId>
        <artifactId>qa-tools-mcp</artifactId>
        <version>1.0.1</version>
    </dependency>

    <!-- Optional: Web UI Support -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>
</dependencies>
```

### Gradle (if using Gradle)

```gradle
dependencies {
    implementation 'com.telemessage.qa:qa-tools-mcp:1.0.1'
    
    // Optional: Web UI Support
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
}
```

---

## Step 2: Configure JFrog Repository

Add Smarsh JFrog Artifactory repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>smarsh-artifactory</id>
        <name>Smarsh JFrog Repository</name>
        <url>https://smarsh.jfrog.io/smarsh/libs-rc</url>
    </repository>
    
    <!-- Spring Milestones (for Spring AI) -->
    <repository>
        <id>spring-milestones</id>
        <name>Spring Milestones</name>
        <url>https://repo.spring.io/milestone</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
    </repository>
</repositories>
```

### Gradle Repository Configuration

```gradle
repositories {
    mavenCentral()
    maven {
        url 'https://smarsh.jfrog.io/smarsh/libs-rc'
    }
    maven {
        url 'https://repo.spring.io/milestone'
    }
}
```

---

## Step 3: Enable MCP Server

Add the `@EnableMcpServer` annotation to your main Spring Boot application class:

```java
package com.yourcompany.projectb;

import com.telemessage.qatools.annotation.EnableMcpServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableMcpServer  // This single annotation enables everything!
public class ProjectBApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ProjectBApplication.class, args);
    }
}
```

**That's it!** The `@EnableMcpServer` annotation automatically:
- ✅ Configures Spring AI MCP server
- ✅ Registers built-in MCP tools
- ✅ Sets up error tracking (LERR system)
- ✅ Initializes metrics collection
- ✅ Enables server details web UI (if web dependencies present)
- ✅ Configures WebSocket for real-time updates
- ✅ Sets up Swagger/OpenAPI documentation

---

## Step 4: Configure Application

Create or update your `application.yml`:

### Minimal Configuration (Uses Defaults)

```yaml
spring:
  application:
    name: project-b
  
  ai:
    mcp:
      server:
        name: ${spring.application.name}
        version: 1.0.0
        type: SYNC

server:
  port: 8080
```

### Full Configuration (All Options)

```yaml
spring:
  application:
    name: project-b
  
  ai:
    mcp:
      server:
        name: ${spring.application.name}
        version: 1.0.0
        description: "Project B MCP Server"
        type: SYNC
        capabilities:
          tool: true
          resource: true
          prompt: true
          completion: true

server:
  port: 8080

# MCP Framework Configuration
mcp:
  server:
    enabled: true
    base-path: /mcp
    enable-web-ui: true
    enable-built-in-tools: true
    enable-swagger: true
    
    # Error Tracking (LERR System)
    error-tracking:
      enabled: true
      max-errors: 1000
      capture-stack-traces: true
      max-stack-trace-depth: 20
    
    # Metrics Collection
    metrics:
      enabled: true
      collection-interval: 5000  # milliseconds
      max-data-points: 100
    
    # Live Logs
    live-logs:
      enabled: true
      buffer-size: 1000
    
    # WebSocket Configuration
    web-socket:
      enabled: true
      endpoint: /mcp-ws
      max-message-size: 65536

# Logging Configuration
logging:
  level:
    root: INFO
    com.yourcompany: DEBUG
    com.telemessage.qatools: DEBUG
  
  # Optional: Enable file logging for Live Logs tab
  file:
    name: logs/application.log
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

# Management Endpoints (Optional)
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

---

## Step 5: Create Custom Tools (Optional)

### Method 1: Using @McpTool Annotation (Recommended)

Create a service class with `@McpTool` annotated methods:

```java
package com.yourcompany.projectb.tools;

import com.telemessage.qatools.error.ErrorTracker;
import com.telemessage.qatools.metrics.McpMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerTools {

    private final ErrorTracker errorTracker;  // Provided by framework
    private final McpMetricsCollector metricsCollector;  // Provided by framework
    private final CustomerService customerService;  // Your business service

    @McpTool(description = "Get customer details by ID")
    public Map<String, Object> getCustomer(
            @McpToolParam(description = "Customer ID", required = true) String customerId) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("Getting customer: {}", customerId);
            
            Customer customer = customerService.findById(customerId);
            
            result.put("success", true);
            result.put("customerId", customer.getId());
            result.put("name", customer.getName());
            result.put("email", customer.getEmail());
            result.put("status", customer.getStatus());
            
            // Record metric
            metricsCollector.recordMetric("customer.queries", 1.0);
            
        } catch (CustomerNotFoundException e) {
            log.warn("Customer not found: {}", customerId);
            result.put("success", false);
            result.put("error", "Customer not found");
            result.put("customerId", customerId);
            
        } catch (Exception e) {
            log.error("Error getting customer: {}", customerId, e);
            
            // Track error with context
            errorTracker.captureError(
                "CustomerTools_getCustomer",
                e,
                "customer-query-failed",
                Map.of("customerId", customerId)
            );
            
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }

    @McpTool(description = "Create a new customer")
    public Map<String, Object> createCustomer(
            @McpToolParam(description = "Customer name", required = true) String name,
            @McpToolParam(description = "Customer email", required = true) String email,
            @McpToolParam(description = "Customer phone", required = false) String phone) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("Creating customer: {} ({})", name, email);
            
            Customer customer = customerService.create(name, email, phone);
            
            result.put("success", true);
            result.put("customerId", customer.getId());
            result.put("message", "Customer created successfully");
            
            // Record metric
            metricsCollector.recordMetric("customer.created", 1.0);
            
        } catch (Exception e) {
            log.error("Error creating customer", e);
            
            errorTracker.captureError(
                "CustomerTools_createCustomer",
                e,
                "customer-creation-failed",
                Map.of("name", name, "email", email)
            );
            
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}
```

### Method 2: Using Function Beans (Spring AI Native)

```java
package com.yourcompany.projectb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.Map;
import java.util.function.Function;

@Configuration
public class ToolsConfiguration {

    @Bean
    @Description("Process order and return confirmation")
    public Function<OrderRequest, Map<String, Object>> processOrder() {
        return request -> {
            // Your business logic here
            String orderId = "ORD-" + System.currentTimeMillis();
            
            return Map.of(
                "success", true,
                "orderId", orderId,
                "customerId", request.getCustomerId(),
                "items", request.getItems(),
                "total", calculateTotal(request)
            );
        };
    }
    
    @Bean
    @Description("Get order status by order ID")
    public Function<String, Map<String, Object>> getOrderStatus() {
        return orderId -> {
            // Query order status
            return Map.of(
                "success", true,
                "orderId", orderId,
                "status", "SHIPPED",
                "trackingNumber", "TRACK123456"
            );
        };
    }
    
    private double calculateTotal(OrderRequest request) {
        // Calculate logic
        return 99.99;
    }
}
```

**Required Imports:**
```java
// For @McpTool annotation approach
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

// For Function beans approach
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import java.util.function.Function;
```

---

## Step 6: Use Framework Components

The framework provides injectable components you can use anywhere in your application:

### ErrorTracker - Track and Query Errors

```java
package com.yourcompany.projectb.service;

import com.telemessage.qatools.error.ErrorTracker;
import com.telemessage.qatools.error.ErrorCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final ErrorTracker errorTracker;

    public void processOrder(Order order) {
        try {
            // Your business logic
            validateOrder(order);
            saveOrder(order);
            notifyCustomer(order);
            
        } catch (ValidationException e) {
            // Track validation errors
            errorTracker.captureError(
                ErrorCategory.GENERAL,
                "OrderService.processOrder",
                e,
                Map.of(
                    "orderId", order.getId(),
                    "customerId", order.getCustomerId(),
                    "validationField", e.getField()
                )
            );
            throw e;
            
        } catch (Exception e) {
            // Track unexpected errors
            errorTracker.captureError(
                ErrorCategory.GENERAL,
                "OrderService.processOrder",
                e,
                Map.of("orderId", order.getId())
            );
            throw new OrderProcessingException("Failed to process order", e);
        }
    }
}
```

### McpMetricsCollector - Record Custom Metrics

```java
package com.yourcompany.projectb.controller;

import com.telemessage.qatools.metrics.McpMetricsCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final McpMetricsCollector metricsCollector;

    @PostMapping
    public OrderResponse createOrder(@RequestBody OrderRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            Order order = orderService.createOrder(request);
            
            // Record success metric
            metricsCollector.recordMetric("orders.created.success", 1.0);
            metricsCollector.recordMetric("orders.total_value", order.getTotalAmount());
            
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordMetric("orders.processing_time_ms", (double) duration);
            
            return OrderResponse.success(order);
            
        } catch (Exception e) {
            // Record failure metric
            metricsCollector.recordMetric("orders.created.failure", 1.0);
            throw e;
        }
    }
}
```

---

## Step 7: Configure .mcp.json for IDE

Create `.mcp.json` in your project root for IDE integration (Windsurf, Cursor, etc.):

```json
{
  "mcpServers": {
    "project-b-mcp": {
      "url": "http://localhost:8080/sse",
      "metadata": {
        "description": "Project B MCP Server - Customer and Order Management",
        "version": "1.0.0",
        "baseUrl": "http://localhost:8080",
        "serverDetailsUrl": "http://localhost:8080/server-details",
        "swaggerUrl": "http://localhost:8080/swagger-ui.html"
      }
    }
  }
}
```

---

## Testing and Verification

### 1. Start Your Application

```bash
mvn spring-boot:run
```

Or if using Gradle:
```bash
./gradlew bootRun
```

### 2. Verify MCP Server is Running

Check the console logs for:
```
INFO  c.t.qatools.autoconfigure.McpServerAutoConfiguration - MCP Server enabled and configured
INFO  c.t.qatools.config.McpToolsConfiguration - Registered 10 built-in MCP tools
INFO  o.s.ai.mcp.server.McpAsyncServer - MCP Server started successfully
```

### 3. Access Server Details Dashboard

Open your browser and navigate to:
```
http://localhost:8080/server-details
```

You should see 6 tabs:
1. **Loggers** - View and manage logger levels
2. **Errors** - Browse captured errors
3. **Metrics** - View system metrics and charts
4. **Tools** - List all available MCP tools
5. **Tests** - Run MCP test suites
6. **Live Logs** - Real-time log streaming

### 4. Test MCP Endpoint

```bash
curl http://localhost:8080/sse
```

Should return SSE connection.

### 5. View Swagger Documentation

```
http://localhost:8080/swagger-ui.html
```

### 6. Test Built-in Tools

The framework provides these built-in tools automatically:

| Tool | Description | Test Command |
|------|-------------|--------------|
| `ping` | Health check | Via Server Details → Tools tab |
| `getSystemInfo` | System information | Via Server Details → Tools tab |
| `getRecentErrors` | Query recent errors | Via Server Details → Errors tab |
| `getMetricsSummary` | Get metrics summary | Via Server Details → Metrics tab |
| `updateLogger` | Change logger level | Via Server Details → Loggers tab |

### 7. Test Your Custom Tools

If you created custom tools, they should appear in:
- Server Details → Tools tab
- Swagger UI
- MCP client (IDE)

---

## Best Practices and Common Pitfalls

### ⚠️ CRITICAL: MCP Tool Return Types

**ALWAYS return `Map<String, Object>` from `@McpTool` methods, NOT DTOs directly.**

#### Why This Matters

The MCP framework uses AOP (Aspect-Oriented Programming) to intercept tool method returns for metrics collection and serialization. This can cause issues with complex return types:

- **Lombok Builders**: DTOs with `@Builder` annotation generate inner builder classes that may fail to serialize with `NoClassDefFoundError`
- **Complex DTOs**: Nested objects, circular references, or custom serializers can cause serialization failures
- **MCP Protocol Compatibility**: The MCP protocol expects simple, serializable data structures

#### ✅ Correct Pattern

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageTool {
    
    private final MessageService messageService;
    
    @McpTool(description = "Send a message")
    public Map<String, Object> sendMessage(String recipient, String content) {
        // Service returns DTO (can use @Builder internally)
        MessageResponse response = messageService.send(recipient, content);
        
        // Convert DTO to Map before returning
        return convertToMap(response);
    }
    
    private Map<String, Object> convertToMap(MessageResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", response.isSuccess());
        result.put("messageId", response.getMessageId());
        result.put("timestamp", response.getTimestamp());
        result.put("statusCode", response.getStatusCode());
        if (response.getErrorMessage() != null) {
            result.put("errorMessage", response.getErrorMessage());
        }
        return result;
    }
}
```

#### ❌ Incorrect Pattern (Will Fail)

```java
@Service
@RequiredArgsConstructor
public class MessageTool {
    
    private final MessageService messageService;
    
    @McpTool(description = "Send a message")
    public MessageResponse sendMessage(String recipient, String content) {
        // ❌ WRONG: Returning DTO directly
        // This will fail if MessageResponse has @Builder annotation
        // or complex nested objects
        return messageService.send(recipient, content);
    }
}
```

#### Error You'll See

If you return DTOs directly, you may encounter:

```
Error in MCP tool execution: com.yourcompany.dto.MessageResponse$MessageResponseBuilder
java.lang.NoClassDefFoundError: com/yourcompany/dto/MessageResponse$MessageResponseBuilder
```

#### Key Takeaways

1. **Service Layer**: Can use DTOs with `@Builder` freely - this is internal
2. **MCP Tool Layer**: Must convert DTOs to `Map<String, Object>` before returning
3. **Conversion**: Create a simple helper method to convert DTOs to Maps
4. **Testing**: Always test your MCP tools end-to-end to catch serialization issues early

### Best Practice: Consistent Response Structure

Use a consistent structure for all tool responses:

```java
Map<String, Object> result = new HashMap<>();
result.put("success", true);           // Always include success flag
result.put("data", actualData);        // Main response data
result.put("message", "Operation completed");  // Human-readable message
result.put("executionTimeMs", duration);       // Performance metric
// Optional error fields
if (error) {
    result.put("success", false);
    result.put("errorMessage", error.getMessage());
    result.put("errorCode", error.getCode());
}
return result;
```

### Best Practice: Use Lombok in Service Layer, Not MCP Layer

```java
// ✅ Service Layer - Use Lombok freely
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private boolean success;
    private String messageId;
    private int statusCode;
    private String errorMessage;
}

// ✅ MCP Tool Layer - Return Maps
@McpTool(description = "Send message")
public Map<String, Object> sendMessage(...) {
    MessageResponse response = service.send(...);
    return convertToMap(response);  // Convert before returning
}
```

---

## Troubleshooting

### Issue: MCP Server Not Starting

**Symptoms:**
- No MCP-related logs in console
- `/server-details` returns 404
- Tools not registered

**Solutions:**
1. Verify `@EnableMcpServer` annotation is present on main application class
2. Check `application.yml` has `mcp.server.enabled: true`
3. Ensure Spring Boot version is 3.3.7 or higher
4. Check for dependency conflicts: `mvn dependency:tree`

### Issue: Tools Not Discovered

**Symptoms:**
- Custom tools don't appear in Server Details → Tools tab
- Tools not available via MCP protocol

**Solutions:**
1. Ensure tool class has `@Service` or `@Component` annotation
2. Verify tool methods have `@McpTool` annotation
3. Check package scanning includes your tools package
4. Review logs for tool registration messages

### Issue: Web UI Not Loading

**Symptoms:**
- `/server-details` returns 404 or blank page
- CSS/JS not loading

**Solutions:**
1. Verify `spring-boot-starter-web` dependency is present
2. Check `spring-boot-starter-thymeleaf` dependency is present
3. Ensure `mcp.server.enable-web-ui: true` in configuration
4. Check browser console for JavaScript errors

### Issue: Errors Not Being Tracked

**Symptoms:**
- Server Details → Errors tab shows no errors
- `errorTracker.captureError()` not working

**Solutions:**
1. Verify `ErrorTracker` is autowired correctly
2. Check `mcp.server.error-tracking.enabled: true`
3. Ensure exceptions are being caught and passed to `errorTracker`
4. Review logs for error tracking initialization

### Issue: Metrics Not Collecting

**Symptoms:**
- Server Details → Metrics tab shows no data
- Charts are empty

**Solutions:**
1. Verify `mcp.server.metrics.enabled: true`
2. Check `McpMetricsCollector` is autowired
3. Ensure metrics are being recorded with `recordMetric()`
4. Wait a few seconds for initial data collection

### Issue: Live Logs Not Streaming

**Symptoms:**
- Live Logs tab shows "No logs available"
- WebSocket connection fails

**Solutions:**
1. Verify `mcp.server.live-logs.enabled: true`
2. Check `mcp.server.web-socket.enabled: true`
3. Ensure `logging.file.name` is configured
4. Check browser console for WebSocket errors
5. Verify firewall/proxy allows WebSocket connections

---

## Advanced Configuration

### Custom Error Categories

```java
package com.yourcompany.projectb.config;

import com.telemessage.qatools.error.ErrorCategory;

public enum CustomErrorCategory {
    PAYMENT_PROCESSING,
    INVENTORY_MANAGEMENT,
    SHIPPING,
    CUSTOMER_VALIDATION;
    
    public ErrorCategory toErrorCategory() {
        return ErrorCategory.GENERAL;  // Map to framework category
    }
}
```

### Custom Metrics

```java
@Service
@RequiredArgsConstructor
public class CustomMetricsService {
    
    private final McpMetricsCollector metricsCollector;
    
    @Scheduled(fixedRate = 60000)  // Every minute
    public void collectBusinessMetrics() {
        // Collect custom business metrics
        long activeOrders = orderRepository.countByStatus("ACTIVE");
        metricsCollector.recordMetric("business.orders.active", (double) activeOrders);
        
        double revenue = orderRepository.sumTotalByDate(LocalDate.now());
        metricsCollector.recordMetric("business.revenue.today", revenue);
    }
}
```

### Security Configuration

The Spring MCP Framework includes a comprehensive, YAML-driven security system that supports three modes:

- **DISABLED**: No security (default for backward compatibility)
- **STANDALONE**: MCP security as the primary authentication mechanism
- **COEXIST**: MCP security alongside existing Spring Security (Basic Auth, OAuth2, etc.)

#### Security Features

✅ **API Key Authentication** - Header-based authentication (X-MCP-API-Key)
✅ **Tool-Level Authorization** - Granular RBAC permissions per tool
✅ **Multiple API Keys** - Different keys with different roles
✅ **WebSocket Security** - Secured real-time connections
✅ **Swagger Integration** - Automatic security documentation

---

#### Mode 1: Security DISABLED (Default)

**Use Case**: Development, testing, or when security is handled by external systems.

```yaml
# application.yaml
mcp:
  server:
    security:
      enabled: false  # Default - no security applied
```

**Characteristics**:
- All endpoints are publicly accessible
- No authentication required
- Backward compatible with existing configurations

**Swagger Behavior**:
- No "Authorize" button shown
- Endpoints appear unlocked (no padlock icon)
- Try-it-out works without credentials

---

#### Mode 2: STANDALONE Security (MCP as Primary Auth)

**Use Case**: MCP security is the only authentication mechanism in your application.

##### Step 1: Add Spring Security Dependency

The framework includes Spring Security as an optional dependency. Ensure it's available:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

**Note**: Already included by `qa-tools-mcp` as an optional dependency.

##### Step 2: Configure STANDALONE Security

```yaml
# application.yaml
mcp:
  server:
    security:
      # Enable security
      enabled: true

      # STANDALONE mode - MCP security is primary
      mode: STANDALONE

      # Authentication configuration
      authentication:
        type: API_KEY

        # Multiple API keys with roles
        api-keys:
          # Load from environment variables (RECOMMENDED)
          - key: "${MCP_USER_KEY}"
            name: "user-client"
            roles: ["USER"]
            enabled: true

          - key: "${MCP_ADMIN_KEY}"
            name: "admin-client"
            roles: ["ADMIN"]
            enabled: true

        # Header name for API key
        header-name: "X-MCP-API-Key"

        # Disallow anonymous access
        allow-anonymous: false

      # Authorization (tool-level permissions)
      authorization:
        enabled: true

        # Default policy for unlisted tools
        # ALLOW: All authenticated users can access unlisted tools
        # DENY: Only explicitly permitted tools are accessible
        default-policy: ALLOW

        # Tool-specific permissions
        tool-permissions:
          # Admin-only tools
          clearErrors: ["ADMIN"]
          updateLogger: ["ADMIN"]

          # User and admin tools
          getRecentErrors: ["USER", "ADMIN"]
          getErrorsByCategory: ["USER", "ADMIN"]
          getErrorsBySeverity: ["USER", "ADMIN"]
          getErrorStatistics: ["USER", "ADMIN"]
          analyzeErrors: ["USER", "ADMIN"]
          getMetricsSummary: ["USER", "ADMIN"]
          getAllMetrics: ["USER", "ADMIN"]
          getSystemInfo: ["USER", "ADMIN"]
          ping: ["USER", "ADMIN"]

      # Endpoint security
      endpoints:
        # Paths that require authentication
        secured-paths:
          - /mcp/**
          - /server-details/**
          - /api/**

        # Paths that are always public
        public-paths:
          - /actuator/health
          - /actuator/info
          - /swagger-ui/**
          - /ui/**
          - /api-docs/**
          - /v3/api-docs/**
```

##### Step 3: Set Environment Variables

**NEVER** hardcode API keys in YAML files. Use environment variables:

```bash
# Linux/Mac
export MCP_USER_KEY="user-key-$(openssl rand -hex 16)"
export MCP_ADMIN_KEY="admin-key-$(openssl rand -hex 16)"

# Windows (PowerShell)
$env:MCP_USER_KEY="user-key-generated-random-key-here"
$env:MCP_ADMIN_KEY="admin-key-generated-random-key-here"
```

**Generate Strong Keys**:
```bash
# Generate 32-character hex key
openssl rand -hex 32
```

##### Step 4: Use API Keys in Requests

**cURL Example**:
```bash
# With user key (can view errors)
curl -H "X-MCP-API-Key: your-user-key-here" \
     http://localhost:8080/mcp/tools/getRecentErrors?limit=10

# With admin key (can clear errors)
curl -X POST \
     -H "X-MCP-API-Key: your-admin-key-here" \
     http://localhost:8080/mcp/tools/clearErrors
```

**Java RestTemplate Example**:
```java
@Service
@RequiredArgsConstructor
public class McpClient {

    @Value("${mcp.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public String getRecentErrors(int limit) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MCP-API-Key", apiKey);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "http://localhost:8080/mcp/tools/getRecentErrors?limit=" + limit,
            HttpMethod.GET,
            request,
            String.class
        );

        return response.getBody();
    }
}
```

**WebClient Example**:
```java
@Service
@RequiredArgsConstructor
public class McpClient {

    @Value("${mcp.api.key}")
    private String apiKey;

    private final WebClient webClient;

    public Mono<String> getRecentErrors(int limit) {
        return webClient.get()
            .uri("/mcp/tools/getRecentErrors?limit=" + limit)
            .header("X-MCP-API-Key", apiKey)
            .retrieve()
            .bodyToMono(String.class);
    }
}
```

**Swagger UI Usage**:
1. Open [http://localhost:8080/ui](http://localhost:8080/ui)
2. Click **"Authorize"** button (🔓 icon at top right)
3. Enter your API key in the value field
4. Click **"Authorize"**
5. Click **"Close"**
6. All subsequent requests will include the API key automatically

**Swagger Behavior** (STANDALONE mode):
- ✅ "Authorize" button visible at top right
- 🔒 Padlock icons on secured endpoints
- Security section in API description shows:
  - Authentication type (API_KEY)
  - Header name (X-MCP-API-Key)
  - Available roles and permissions
  - Example requests with API keys
  - Restricted tools list

---

#### Mode 3: COEXIST Security (With Existing Spring Security)

**Use Case**: Your application already has Spring Security (Basic Auth, OAuth2, JWT, etc.) and you want to add MCP tools with separate API key authentication.

##### Step 1: Keep Your Existing Security Configuration

Your existing Spring Security configuration remains unchanged:

```java
@Configuration
@EnableWebSecurity
public class ExistingSecurityConfig {

    @Bean
    @Order(100)  // Default order
    public SecurityFilterChain existingFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**", "/admin/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").hasRole("USER")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
```

##### Step 2: Enable COEXIST Mode

```yaml
# application.yaml
mcp:
  server:
    security:
      # Enable security
      enabled: true

      # COEXIST mode - work alongside existing Spring Security
      mode: COEXIST

      # Authentication configuration
      authentication:
        type: API_KEY
        api-keys:
          - key: "${MCP_API_KEY}"
            name: "mcp-client"
            roles: ["USER", "ADMIN"]
            enabled: true

        header-name: "X-MCP-API-Key"
        allow-anonymous: false

      # Authorization
      authorization:
        enabled: true
        default-policy: ALLOW
        tool-permissions:
          clearErrors: ["ADMIN"]
          updateLogger: ["ADMIN"]

      # Only secure MCP paths
      endpoints:
        secured-paths:
          - /mcp/**
          - /server-details/**
        public-paths:
          - /actuator/health
          - /swagger-ui/**
          - /ui/**

      # Integration settings for COEXIST mode
      integration:
        # MCP filter runs at order 90 (before Basic Auth at 100)
        order: 90

        # Allow other auth providers if API key auth fails
        allow-other-providers: true

        # Don't share security context (keep separate)
        share-security-context: false
```

##### Step 3: How It Works

**Filter Chain Order**:
1. **MCP Security Filter** (@Order(90)) - Handles `/mcp/**` paths with API keys
2. **Existing Security Filter** (@Order(100)) - Handles `/api/**` paths with Basic Auth

**Request Routing**:
- `/mcp/tools` → Requires API key (X-MCP-API-Key header)
- `/api/users` → Requires Basic Auth (Authorization header)
- `/server-details` → Requires API key
- `/actuator/health` → Public (no auth)

**Example Requests**:
```bash
# Access MCP tools with API key
curl -H "X-MCP-API-Key: your-api-key" \
     http://localhost:8080/mcp/tools/getSystemInfo

# Access existing API with Basic Auth
curl -u username:password \
     http://localhost:8080/api/users

# Both work independently!
```

**Swagger Behavior** (COEXIST mode):
- Shows security schemes for BOTH auth methods
- MCP endpoints show API key requirement
- Existing API endpoints show Basic Auth requirement
- Users can authorize with different credentials for each

---

#### Local Development Profile with Security

For local development, the framework provides a pre-configured profile with security enabled and test API keys:

```yaml
# Run with: mvn spring-boot:run -Dspring-boot.run.profiles=local
# Or: export SPRING_PROFILES_ACTIVE=local

# application-local.yaml (already configured)
mcp:
  server:
    security:
      enabled: true
      mode: STANDALONE

      authentication:
        api-keys:
          # Pre-configured dev keys (override with env vars)
          - key: "${MCP_DEV_USER_KEY:dev-user-key-12345-local-only}"
            name: "dev-user"
            roles: ["USER"]

          - key: "${MCP_DEV_ADMIN_KEY:dev-admin-key-67890-local-only}"
            name: "dev-admin"
            roles: ["ADMIN"]

          - key: "${MCP_DEV_TEST_KEY:dev-test-key-abcde-local-only}"
            name: "dev-test"
            roles: ["USER", "ADMIN", "TESTER"]
```

**Default Development Keys**:
- **User Key**: `dev-user-key-12345-local-only` (Can view errors, metrics)
- **Admin Key**: `dev-admin-key-67890-local-only` (Can clear errors, update loggers)
- **Test Key**: `dev-test-key-abcde-local-only` (All permissions)

⚠️ **WARNING**: These keys are for LOCAL DEVELOPMENT ONLY. Never use in production!

**Test Locally**:
```bash
# Start with local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Test with dev keys
curl -H "X-MCP-API-Key: dev-admin-key-67890-local-only" \
     http://localhost:8035/mcp/tools/getSystemInfo
```

See [LOCAL_SECURITY_SETUP.md](../../LOCAL_SECURITY_SETUP.md) for complete local development guide.

---

#### Tool-Level Permissions

Control which roles can access specific tools:

```yaml
mcp:
  server:
    security:
      authorization:
        enabled: true

        # Default policy for unlisted tools
        default-policy: ALLOW  # or DENY

        # Tool-specific permissions
        tool-permissions:
          # Admin-only tools
          clearErrors: ["ADMIN"]
          updateLogger: ["ADMIN"]
          deployToProduction: ["ADMIN", "DEPLOYER"]

          # Read-only tools (all authenticated users)
          getRecentErrors: ["USER", "ADMIN", "VIEWER"]
          getMetricsSummary: ["USER", "ADMIN", "VIEWER"]

          # Custom roles
          processPayment: ["PAYMENT_PROCESSOR"]
          accessCustomerData: ["CUSTOMER_SERVICE", "ADMIN"]
```

**Custom Tool Example**:
```java
@Service
public class PaymentTools {

    @McpTool(description = "Process a payment")
    public Map<String, Object> processPayment(String orderId, double amount) {
        // This tool requires PAYMENT_PROCESSOR role
        // Configured in tool-permissions above

        Map<String, Object> result = new HashMap<>();
        // Process payment logic...
        result.put("success", true);
        result.put("transactionId", "TXN-12345");
        return result;
    }
}
```

**How It Works**:
1. User makes request with API key
2. Framework validates API key and extracts roles
3. Before executing tool, checks if user has required role
4. If authorized, tool executes
5. If not authorized, returns 403 Forbidden

**Access Denied Response**:
```json
{
  "error": "Access Denied",
  "message": "User does not have permission to access tool: processPayment",
  "requiredRoles": ["PAYMENT_PROCESSOR"],
  "userRoles": ["USER"],
  "timestamp": "2025-01-08T10:30:00Z"
}
```

---

#### WebSocket Security

WebSocket connections are automatically secured when security is enabled.

**JavaScript/Browser Example**:
```javascript
// Option 1: API key in header (preferred)
const socket = new SockJS('http://localhost:8080/mcp-ws', null, {
    headers: {
        'X-MCP-API-Key': 'your-api-key-here'
    }
});

const stompClient = Stomp.over(socket);
stompClient.connect({}, function(frame) {
    console.log('Connected: ' + frame);

    // Subscribe to live logs
    stompClient.subscribe('/topic/live-logs', function(message) {
        console.log('Log:', message.body);
    });
});

// Option 2: API key in query parameter (if headers not supported)
const socketWithQuery = new SockJS(
    'http://localhost:8080/mcp-ws?apiKey=your-api-key-here'
);
```

**Java WebSocket Client Example**:
```java
@Service
public class McpWebSocketClient {

    @Value("${mcp.api.key}")
    private String apiKey;

    public void connect() {
        WebSocketClient client = new StandardWebSocketClient();
        WebSocketStompClient stompClient = new WebSocketStompClient(client);

        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders headers = new StompHeaders();
        headers.add("X-MCP-API-Key", apiKey);

        StompSession session = stompClient.connect(
            "ws://localhost:8080/mcp-ws",
            new WebSocketHttpHeaders(),
            headers,
            new StompSessionHandlerAdapter() {}
        ).get();

        session.subscribe("/topic/live-logs", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                System.out.println("Log: " + payload);
            }
        });
    }
}
```

**Security Validation**:
- API key validated during WebSocket handshake
- Invalid keys result in HTTP 401 Unauthorized
- Authentication stored in WebSocket session
- No re-authentication needed during session

---

#### Testing Secured Endpoints

##### Manual Testing with cURL

```bash
# Test without API key (should fail with 401)
curl -v http://localhost:8080/mcp/tools/getSystemInfo
# Expected: HTTP/1.1 401 Unauthorized

# Test with invalid API key (should fail with 401)
curl -v -H "X-MCP-API-Key: invalid-key" \
     http://localhost:8080/mcp/tools/getSystemInfo
# Expected: HTTP/1.1 401 Unauthorized

# Test with valid user key (should succeed)
curl -H "X-MCP-API-Key: dev-user-key-12345-local-only" \
     http://localhost:8080/mcp/tools/getSystemInfo
# Expected: HTTP/1.1 200 OK with system info

# Test admin-only tool with user key (should fail with 403)
curl -X POST \
     -H "X-MCP-API-Key: dev-user-key-12345-local-only" \
     http://localhost:8080/mcp/tools/clearErrors
# Expected: HTTP/1.1 403 Forbidden

# Test admin-only tool with admin key (should succeed)
curl -X POST \
     -H "X-MCP-API-Key: dev-admin-key-67890-local-only" \
     http://localhost:8080/mcp/tools/clearErrors
# Expected: HTTP/1.1 200 OK
```

##### Integration Tests

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class McpSecurityIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testWithoutApiKey_shouldReturn401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/mcp/tools/getSystemInfo",
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void testWithValidUserKey_shouldSucceed() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MCP-API-Key", "test-user-key");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/mcp/tools/getSystemInfo",
            HttpMethod.GET,
            request,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testAdminToolWithUserKey_shouldReturn403() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MCP-API-Key", "test-user-key");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/mcp/tools/clearErrors",
            HttpMethod.POST,
            request,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void testAdminToolWithAdminKey_shouldSucceed() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MCP-API-Key", "test-admin-key");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/mcp/tools/clearErrors",
            HttpMethod.POST,
            request,
            String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

```yaml
# application-test.yaml
mcp:
  server:
    security:
      enabled: true
      mode: STANDALONE
      authentication:
        api-keys:
          - key: "test-user-key"
            name: "test-user"
            roles: ["USER"]
          - key: "test-admin-key"
            name: "test-admin"
            roles: ["ADMIN"]
```

---

#### Security Best Practices

##### 1. Use Environment Variables for API Keys

```yaml
# ✅ GOOD - Load from environment
api-keys:
  - key: "${MCP_USER_KEY}"
    name: "user-client"
    roles: ["USER"]

# ❌ BAD - Hardcoded (security risk!)
api-keys:
  - key: "my-secret-key-12345"
    name: "user-client"
    roles: ["USER"]
```

##### 2. Generate Strong, Random Keys

```bash
# Generate 32-character hex key (256-bit)
openssl rand -hex 32

# Example output:
# 8f3d5b7a9c1e2f4d6b8a0c2e4f6d8b0a1c3e5f7d9b1a3c5e7f9d1b3a5c7e9f1d
```

##### 3. Different Keys for Different Environments

```bash
# Development
export MCP_API_KEY="dev-key-simple-for-testing"

# Staging
export MCP_API_KEY="stg-8f3d5b7a9c1e2f4d6b8a0c2e4f6d8b0a"

# Production
export MCP_API_KEY="prd-$(openssl rand -hex 32)"
```

##### 4. Rotate Keys Regularly

```yaml
api-keys:
  # New key
  - key: "${MCP_NEW_KEY}"
    name: "client-v2"
    roles: ["USER"]
    enabled: true

  # Old key (keep during migration)
  - key: "${MCP_OLD_KEY}"
    name: "client-v1"
    roles: ["USER"]
    enabled: true  # Disable after migration complete
```

##### 5. Principle of Least Privilege

```yaml
# ✅ GOOD - Minimal permissions
tool-permissions:
  getRecentErrors: ["USER", "ADMIN"]
  clearErrors: ["ADMIN"]  # Only admins

# ❌ BAD - Too permissive
authorization:
  default-policy: ALLOW  # Everyone can do everything
```

##### 6. Use HTTPS in Production

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${SSL_PASSWORD}
    key-store-type: PKCS12
    key-alias: mcp-server
```

##### 7. Monitor Security Events

Security events (authentication failures, access denied) are tracked in ErrorTracker:

```bash
# View security events
curl -H "X-MCP-API-Key: admin-key" \
     http://localhost:8080/mcp/tools/getErrorsByCategory?category=TOOLS

# Look for:
# - "API key authentication failed"
# - "Access denied to tool: X"
# - "Invalid API key provided"
```

##### 8. Disable Security Features Not Needed

```yaml
mcp:
  server:
    security:
      # Disable authorization if only authentication needed
      authorization:
        enabled: false  # All authenticated users can access all tools

      # Or disable security entirely if not needed
      enabled: false
```

---

#### Troubleshooting Security Issues

##### Issue: 401 Unauthorized with Valid Key

**Symptoms**: Requests return 401 even with correct API key

**Check**:
1. Verify key is exactly correct (no extra spaces)
2. Check header name matches: `X-MCP-API-Key`
3. Ensure key is `enabled: true` in configuration
4. Check logs for "API key authentication failed"
5. Verify environment variable is set correctly

**Debug**:
```yaml
logging:
  level:
    com.telemessage.qatools.security: DEBUG
    org.springframework.security: DEBUG
```

##### Issue: 403 Forbidden / Access Denied

**Symptoms**: Authentication works, but tool access is denied

**Check**:
1. Verify user has required role for the tool
2. Check `tool-permissions` configuration
3. Review logs for "Access denied to tool: X"
4. Check `default-policy` setting (ALLOW vs DENY)

**Debug**:
```bash
# Try with admin key to verify tool works
curl -H "X-MCP-API-Key: admin-key" ...
```

##### Issue: Swagger "Authorize" Button Missing

**Symptoms**: No authorize button in Swagger UI

**Check**:
1. Verify `mcp.server.security.enabled=true`
2. Check `springdoc.api-docs.enabled=true`
3. Restart application
4. Clear browser cache

##### Issue: WebSocket Connection Fails

**Symptoms**: WebSocket handshake fails with 401

**Check**:
1. Include API key in header or query parameter
2. Check CORS settings for WebSocket
3. Verify WebSocket endpoint is correct (`/mcp-ws`)
4. Check browser console for errors

---

#### Security Documentation References

For complete security documentation, see:
- **[SECURITY.md](../../SECURITY.md)** - Comprehensive security guide (500+ lines)
- **[LOCAL_SECURITY_SETUP.md](../../LOCAL_SECURITY_SETUP.md)** - Local development setup
- **[SECURITY_IMPLEMENTATION_SUMMARY.md](../../SECURITY_IMPLEMENTATION_SUMMARY.md)** - Technical implementation

**Example Configuration Files**:
- `src/main/resources/application-security-standalone.yaml` - STANDALONE mode example
- `src/main/resources/application-security-coexist.yaml` - COEXIST mode example
- `src/main/resources/application-local.yaml` - Local development with security enabled

### CORS Configuration

```yaml
mcp:
  server:
    cors:
      allowed-origins:
        - "http://localhost:3000"
        - "https://your-frontend.com"
      allowed-methods:
        - GET
        - POST
        - OPTIONS
      allowed-headers:
        - "*"
      allow-credentials: true
```

### Custom Tool Naming

```java
@McpTool(description = "Detailed description for AI clients")
public Map<String, Object> customToolName(  // Method name becomes tool name
    @McpToolParam(
        description = "What this parameter does",
        required = true
    ) String inputParam) {  // Parameter name is used as-is
    // Implementation
    Map<String, Object> result = new HashMap<>();
    result.put("success", true);
    result.put("data", "processed: " + inputParam);
    return result;
}
```

---

## Production Checklist

Before deploying to production:

### General Configuration
- [ ] Set `mcp.server.error-tracking.max-errors` to appropriate value (default: 1000)
- [ ] Configure proper logging levels (INFO or WARN for production)
- [ ] Review and adjust metrics collection interval
- [ ] Set up log rotation for file logging
- [ ] Configure proper session management
- [ ] Document all custom tools for your team
- [ ] Set up backup for error and metrics data if needed

### Security (CRITICAL)
- [ ] **Enable security**: Set `mcp.server.security.enabled=true`
- [ ] **Choose appropriate mode**: STANDALONE or COEXIST
- [ ] **Use strong API keys**: Generate with `openssl rand -hex 32`
- [ ] **Environment variables**: Load ALL API keys from environment variables (NEVER hardcode)
- [ ] **Tool permissions**: Configure granular tool-level permissions based on principle of least privilege
- [ ] **HTTPS enabled**: Configure SSL/TLS certificates for all endpoints
- [ ] **Authentication monitoring**: Set up alerts for repeated authentication failures
- [ ] **Key rotation plan**: Establish process for regular API key rotation
- [ ] **Secure /server-details**: Ensure server details UI requires authentication
- [ ] **Review public paths**: Verify only intended endpoints are in `public-paths` list
- [ ] **Test security**: Run security integration tests before deployment
- [ ] **Audit logging**: Enable DEBUG logging for `com.telemessage.qatools.security` initially

### Network & Integration
- [ ] Set up CORS properly if accessed from web clients
- [ ] Configure rate limiting for MCP endpoints
- [ ] Test WebSocket connections through load balancers
- [ ] Verify WebSocket security with API keys through proxy/load balancer
- [ ] Set up monitoring and alerting for error spikes

### Swagger/OpenAPI
- [ ] Verify Swagger shows security schemes correctly
- [ ] Test "Authorize" button with production API keys
- [ ] Ensure Swagger is accessible only to authorized users (or disable in production)
- [ ] Review API documentation for sensitive information exposure

---

## Next Steps

1. **Explore Built-in Tools**: Test all built-in MCP tools via Server Details UI
2. **Create Custom Tools**: Build tools specific to your business domain
3. **Integrate with AI**: Connect your MCP server to AI clients (Claude, GPT, etc.)
4. **Monitor and Optimize**: Use metrics and error tracking to improve performance
5. **Share with Team**: Document your custom tools and share the Server Details URL

---

## Support and Resources

- **Framework Documentation**: See main README.md
- **Example Project**: Check `examples/projectb/` directory
- **Confluence**: https://smarsh.atlassian.net/wiki/spaces/CMT/pages/4735074423/qa-tools-mcp
- **Spring AI Docs**: https://docs.spring.io/spring-ai/reference/
- **MCP Specification**: https://modelcontextprotocol.io/

---

**You're all set!** Your Spring Boot application is now MCP-enabled and ready to serve AI clients. 🎉
