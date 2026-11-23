package com.nguay097.moba_analytics.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for the application.
 *
 * Catches exceptions thrown during API request processing and maps them to appropriate
 * HTTP response codes and error messages. Ensures consistent error response formatting
 * across all endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles RiotApiException by mapping the Riot API status code to an HTTP response.
     *
     * Converts Riot API errors (e.g., 404 for not found, 429 for rate limited, 503 for unavailable)
     * into corresponding HTTP responses with a standardized error body containing timestamp,
     * status code, and error message.
     *
     * @param ex the RiotApiException thrown during API processing
     * @return a ResponseEntity with the appropriate HTTP status and error details
     */
    @ExceptionHandler(RiotApiException.class)
    public ResponseEntity<Object> handleRiotApiException(RiotApiException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", ex.getStatusCode());
        body.put("error", mapStatusCodeToMessage(ex.getStatusCode()));
        body.put("message", ex.getMessage());

        HttpStatus httpStatus = HttpStatus.resolve(ex.getStatusCode());
        if (httpStatus == null) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return new ResponseEntity<>(body, httpStatus);
    }

    /**
     * Maps an HTTP status code to a standard error message.
     *
     * @param statusCode the HTTP status code
     * @return a human-readable error message for the status code
     */
    private String mapStatusCodeToMessage(int statusCode) {
        return switch (statusCode) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> "Error";
        };
    }

    /**
     * Handles generic exceptions not caught by specific handlers.
     *
     * @param ex the exception that was not handled by specific exception handlers
     * @return a ResponseEntity with a 500 Internal Server Error status and error details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", 500);
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred");

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
