package com.smart.agent.config;

import com.smart.agent.exception.RateLimitExceededException;
import com.smart.agent.exception.SessionBusyException;
import com.smart.agent.model.ServiceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers.
 *
 * @description Provides unified error response formatting and prevents internal error details
 *              from leaking to clients. Maps known exceptions to appropriate HTTP status codes
 *              and generic error messages.
 * @author Jiangbo Li
 * @date 2026-06-18
 * @version 1.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ServiceResponse<Object> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", details);
        return ServiceResponse.failed("Invalid request: " + details, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ServiceResponse<Object> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing parameter: {}", ex.getParameterName());
        return ServiceResponse.failed("Missing required parameter: " + ex.getParameterName(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ServiceResponse<Object> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ServiceResponse.failed("Malformed request body", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ServiceResponse<Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ServiceResponse.failed("Method not allowed: " + ex.getMethod(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ServiceResponse<Object> handleNotFound(NoResourceFoundException ex) {
        return ServiceResponse.failed("Resource not found", null);
    }

    @ExceptionHandler(SessionBusyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ServiceResponse<Object> handleSessionBusy(SessionBusyException ex) {
        log.warn("Session busy: {}", ex.getMessage());
        return ServiceResponse.failed(ex.getMessage(), null);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ServiceResponse<Object> handleRateLimit(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ServiceResponse.failed("Too many requests, please try again later", null);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ServiceResponse<Object> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return ServiceResponse.failed(ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ServiceResponse<Object> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ServiceResponse.failed("Internal server error", null);
    }
}
