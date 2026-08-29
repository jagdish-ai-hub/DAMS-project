package com.dams.common.exception;

import com.dams.common.dto.ApiError;
import com.dams.common.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * Centralised error handler. Every response carries the request ID for traceability.
 * Error messages always name what failed — never "an error occurred". See AGENT.md.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DamsException.class)
    public ResponseEntity<ApiError> handleDamsException(DamsException ex, HttpServletRequest request) {
        log.warn("DamsException [{}]: {}", ex.getStatus(), ex.getMessage());
        return buildResponse(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        // Don't reveal which field was wrong — just say invalid credentials
        return buildResponse(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied");
    }

    /**
     * A DB constraint we chose to enforce in the schema rather than only in code — most
     * often the partial unique index that allows just one open receive document per job
     * card. Surface it as a 409, not a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        String message = "This change conflicts with an existing record";
        if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("one_open_per_job_card")) {
            message = "This job card already has an open receive document — add the payment to it, "
                + "don't create a second";
        }
        return buildResponse(HttpStatus.CONFLICT, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Attachment is larger than the 10 MB limit");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred — check logs for requestId " + MDC.get(RequestIdFilter.MDC_KEY));
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String message) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        ApiError error = new ApiError(requestId, status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(error);
    }
}
