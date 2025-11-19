package com.telemessage.simulators.controllers;

import com.telemessage.qatools.error.ErrorTracker;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Map;

/**
 * Global exception handler for REST API endpoints
 * Ensures all errors return JSON responses instead of HTML error pages
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorTracker errorTracker;

    public GlobalExceptionHandler(ErrorTracker errorTracker) {
        this.errorTracker = errorTracker;
    }

    /**
     * Handle encoding-related exceptions
     */
    @ExceptionHandler({UnsupportedEncodingException.class, UnsupportedCharsetException.class})
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleEncodingException(Exception ex) {
        log.error("Encoding error: {}", ex.getMessage(), ex);
        errorTracker.captureError(
            "GlobalExceptionHandler.handleEncodingException",
            ex,
            "encoding-error",
            Map.of(
                "operation", "handle_encoding_exception",
                "message", ex.getMessage()
            )
        );
        ErrorResponse response = new ErrorResponse(
                "ENCODING_ERROR",
                "Message encoding is not supported: " + ex.getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.badRequest()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(response);
    }

    /**
     * Handle invalid JSON or malformed request body
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleInvalidJsonException(HttpMessageNotReadableException ex) {
        log.error("Invalid JSON in request: {}", ex.getMessage());
        errorTracker.captureError(
            "GlobalExceptionHandler.handleInvalidJsonException",
            ex,
            "invalid-json-error",
            Map.of(
                "operation", "handle_invalid_json"
            )
        );
        ErrorResponse response = new ErrorResponse(
                "INVALID_REQUEST",
                "Request body is not valid JSON: " + ex.getMostSpecificCause().getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.badRequest()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(response);
    }

    /**
     * Handle missing request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleMissingParameterException(MissingServletRequestParameterException ex) {
        log.error("Missing required parameter: {}", ex.getParameterName());
        errorTracker.captureError(
            "GlobalExceptionHandler.handleMissingParameterException",
            ex,
            "missing-parameter-error",
            Map.of(
                "operation", "handle_missing_parameter",
                "parameterName", ex.getParameterName()
            )
        );
        ErrorResponse response = new ErrorResponse(
                "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing",
                HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.badRequest()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(response);
    }

    /**
     * Handle type mismatch in request parameters
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.error("Type mismatch for parameter '{}': expected {}, got '{}'",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown",
                ex.getValue());
        errorTracker.captureError(
            "GlobalExceptionHandler.handleTypeMismatchException",
            ex,
            "type-mismatch-error",
            Map.of(
                "operation", "handle_type_mismatch",
                "parameterName", ex.getName(),
                "expectedType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
            )
        );
        ErrorResponse response = new ErrorResponse(
                "INVALID_PARAMETER_TYPE",
                String.format("Parameter '%s' should be of type %s, but got '%s'",
                        ex.getName(),
                        ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown",
                        ex.getValue()),
                HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.badRequest()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(response);
    }

    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.error("Validation error: {}", ex.getMessage());
        errorTracker.captureError(
            "GlobalExceptionHandler.handleValidationException",
            ex,
            "validation-error",
            Map.of(
                "operation", "handle_validation"
            )
        );
        StringBuilder message = new StringBuilder("Validation failed: ");
        ex.getBindingResult().getFieldErrors().forEach(error ->
                message.append(error.getField()).append(" - ").append(error.getDefaultMessage()).append("; ")
        );
        ErrorResponse response = new ErrorResponse(
                "VALIDATION_ERROR",
                message.toString(),
                HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.badRequest()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(response);
    }

    /**
     * Handle IllegalArgumentException (often used for business logic validation)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("Invalid argument: {}", ex.getMessage());
        errorTracker.captureError(
            "GlobalExceptionHandler.handleIllegalArgumentException",
            ex,
            "illegal-argument-error",
            Map.of(
                "operation", "handle_illegal_argument",
                "message", ex.getMessage()
            )
        );
        ErrorResponse response = new ErrorResponse(
                "INVALID_ARGUMENT",
                ex.getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.badRequest()
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(response);
    }

    /**
     * Handle general exceptions
     * This is the catch-all for any unhandled exceptions
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        errorTracker.captureError(
            "GlobalExceptionHandler.handleGeneralException",
            ex,
            "unexpected-error",
            Map.of(
                "operation", "handle_general_exception"
            )
        );
        ErrorResponse response = new ErrorResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred: " + ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json;charset=UTF-8")
                .body(response);
    }

    /**
     * Error response structure
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String errorCode;
        private String message;
        private int statusCode;
        private long timestamp = System.currentTimeMillis();

        public ErrorResponse(String errorCode, String message, int statusCode) {
            this.errorCode = errorCode;
            this.message = message;
            this.statusCode = statusCode;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
