package com.dams.myentries.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row in the cashier's "My Entries" list. Stage 4 only has receipts ({@code kind =
 * "RECEIPT"}); Stage 5 adds expenses. {@code queried} rows are highlighted and open in edit
 * mode for fix-and-resubmit (AGENT.md decision #8).
 */
public record MyEntryResponse(
    Long id,
    String kind,
    String documentNo,          // null while still a draft
    String workflowStatus,
    boolean settled,
    Long jobCardId,
    String jobCardReference,
    String customerName,
    BigDecimal totalReceived,
    BigDecimal pendingAmount,
    int lineCount,
    boolean today,
    boolean queried,
    Instant createdAt,
    Instant submittedAt
) {
}
