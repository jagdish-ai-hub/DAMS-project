package com.dams.review.dto;

import java.util.List;

public record BulkVerifyResponse(
    int verifiedCount,
    List<Long> verifiedIds,
    List<String> skippedReasons
) {}
