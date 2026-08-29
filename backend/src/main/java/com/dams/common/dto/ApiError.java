package com.dams.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * Unified error response shape.
 * requestId links this response to the exact server log line — see AGENT.md.
 * Example:
 * {
 *   "requestId": "550e8400-...",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "AppUser with email x@y.com not found",
 *   "timestamp": "2026-07-26T10:12:00Z"
 * }
 */
@Getter
public class ApiError {

    private final String requestId;
    private final int status;
    private final String error;
    private final String message;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final OffsetDateTime timestamp;

    public ApiError(String requestId, int status, String error, String message) {
        this.requestId = requestId;
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = OffsetDateTime.now();
    }
}
