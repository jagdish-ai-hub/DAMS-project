package com.dams.review.dto;

import java.util.List;

/** Result of a bulk Accountant direct-approve — see ReviewService#bulkDirectApproveReceipts. */
public record BulkApproveResponse(
    int approvedCount,
    List<Long> approvedIds,
    List<String> skippedReasons
) {}
