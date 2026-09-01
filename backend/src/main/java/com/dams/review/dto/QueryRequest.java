package com.dams.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The question sent back to the cashier when a document is queried. */
public record QueryRequest(
    @NotBlank(message = "A question for the cashier is required")
    @Size(max = 500, message = "Keep the question under 500 characters")
    String note
) {
}
