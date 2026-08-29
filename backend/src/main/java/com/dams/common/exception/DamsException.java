package com.dams.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base application exception.
 * Always carries a human-readable message naming what failed (entity, ID, org).
 * Never throws "an error occurred" — see AGENT.md coding standards.
 */
public class DamsException extends RuntimeException {

    private final HttpStatus status;

    public DamsException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    // --- Convenience factories ---

    public static DamsException notFound(String entityType, Object id) {
        return new DamsException(HttpStatus.NOT_FOUND,
            entityType + " with id " + id + " not found");
    }

    public static DamsException notFound(String entityType, String field, Object value) {
        return new DamsException(HttpStatus.NOT_FOUND,
            entityType + " with " + field + " '" + value + "' not found");
    }

    public static DamsException badRequest(String message) {
        return new DamsException(HttpStatus.BAD_REQUEST, message);
    }

    public static DamsException forbidden(String message) {
        return new DamsException(HttpStatus.FORBIDDEN, message);
    }

    public static DamsException conflict(String message) {
        return new DamsException(HttpStatus.CONFLICT, message);
    }
}
