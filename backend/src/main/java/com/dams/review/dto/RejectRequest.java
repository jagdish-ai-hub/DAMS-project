package com.dams.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The reason a document is rejected — recorded permanently on its history. */
public record RejectRequest(
    @NotBlank(message = "A reason for rejection is required")
    @Size(max = 500, message = "Keep the reason under 500 characters")
    String reason
) {
}
