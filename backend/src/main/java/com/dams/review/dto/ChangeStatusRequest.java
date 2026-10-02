package com.dams.review.dto;

import jakarta.validation.constraints.NotNull;

/** Reviewer's new business status for an expense (rev 58). */
public record ChangeStatusRequest(@NotNull(message = "businessStatusId is required") Long businessStatusId) {
}
