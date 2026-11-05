package com.artsyvibe.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Turns exceptions thrown by REST controllers into the {@code {success:false, message}} shape the
 * front end already understands, with the right HTTP status. Unexpected errors are logged and their
 * details are not leaked to the client.
 */
@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        return error(HttpStatus.valueOf(ex.getStatusCode().value()), message);
    }

    /** Services signal invalid input / business-rule violations with IllegalArgumentException. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Invalid request.");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<Map<String, Object>> handleUnreadable(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request.");
    }

    @ExceptionHandler({ PessimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class })
    public ResponseEntity<Map<String, Object>> handleConflict(Exception ex) {
        log.warn("Concurrent update conflict: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, "Someone else updated this at the same time. Please try again.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled API error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.");
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        String safeMessage = message != null ? message : status.getReasonPhrase();
        return ResponseEntity.status(status).body(Map.of("success", false, "message", safeMessage));
    }
}
