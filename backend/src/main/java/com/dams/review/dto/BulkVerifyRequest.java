package com.dams.review.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkVerifyRequest(
    @NotEmpty(message = "Select at least one document to verify")
    List<Long> ids
) {}
