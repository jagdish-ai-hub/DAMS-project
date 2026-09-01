package com.dams.jobcard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * The Finance Manager's final close of a Warranty / AMC / CG claim. {@code finalAmount} is
 * the settled figure (0 is allowed — a fully written-off claim). {@code reason} is required
 * only when {@code finalAmount} differs from what was actually received; the server enforces
 * that.
 */
public record CloseClaimRequest(
    @NotNull(message = "A final amount is required")
    @PositiveOrZero(message = "The final amount cannot be negative")
    BigDecimal finalAmount,

    @Size(max = 300, message = "Keep the reason under 300 characters")
    String reason
) {
}
