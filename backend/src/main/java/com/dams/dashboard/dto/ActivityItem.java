package com.dams.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One line on the recent-activity feed — a humanised audit event with its document context. */
public record ActivityItem(
    String actor,
    String action,          // "Approved", "Verified", "Queried", "Closed", …
    String documentNo,      // the document / job-card reference, or null
    String description,     // party / category, one line
    BigDecimal amount,      // the document's amount, or null
    String branchCode,
    Instant at
) {
}
