package com.uttkarsh.billsync.web;

import com.uttkarsh.billsync.dto.ApiError;
import com.uttkarsh.billsync.service.BadRequestException;
import com.uttkarsh.billsync.service.ConflictException;
import com.uttkarsh.billsync.service.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * One error shape for every failure, so a client never has to guess:
 * <pre>{ "timestamp", "status", "error", "details": [...] }</pre>
 */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> beanValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return error(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> badRequest(BadRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, "Validation failed", List.of(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex) {
        // Jackson's messages are long and multi-line; the first line says what went wrong.
        String cause = ex.getMostSpecificCause().getMessage();
        String detail = cause == null ? "Request body could not be read" : cause.lines().findFirst().orElse(cause);
        return error(HttpStatus.BAD_REQUEST, "Malformed request", List.of(detail));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Validation failed",
                List.of(ex.getName() + ": '" + ex.getValue() + "' is not a valid value"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Not found", List.of(ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noSuchEndpoint(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Not found", List.of("No endpoint for " + ex.getHttpMethod() + " /" + ex.getResourcePath()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", List.of(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, "Conflict", List.of(ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> constraintViolation(DataIntegrityViolationException ex) {
        // The service layer checks first; this is the safety net for races and anything it missed.
        log.warn("Database constraint rejected a request", ex);
        return error(HttpStatus.CONFLICT, "Conflict", List.of("The request conflicts with existing data"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", List.of());
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String error, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), error, details));
    }
}
