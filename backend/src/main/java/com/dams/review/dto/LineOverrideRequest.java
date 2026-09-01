package com.dams.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * An Accountant's provisional amount override on one line. The reason is always required —
 * the override and its reason show on the record permanently (AGENT.md "Overridden").
 */
public record LineOverrideRequest(
    @NotNull(message = "A new amount is required")
    @Positive(message = "The amount must be greater than 0")
    BigDecimal amount,

    @NotBlank(message = "A reason for the override is required")
    @Size(max = 300, message = "Keep the reason under 300 characters")
    String reason
) {
}
