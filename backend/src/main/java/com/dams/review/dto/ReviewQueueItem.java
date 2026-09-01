package com.dams.review.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row in the Accountant's review queue. Deliberately flat and cheap — the queue pane
 * and its overview panel (counts, total value, by-branch, preview) are all derived on the
 * frontend from a list of these. Open a row to load the full document via its normal
 * {@code GET /api/v1/{receipts|expenses}/{id}} endpoint.
 */
public record ReviewQueueItem(
    String type,            // "receipt" | "expense"
    Long id,
    String documentNo,
    Long branchId,
    String branchCode,
    String partyName,       // customer (receipt) or receiver (expense)
    String categoryName,
    BigDecimal amount,      // Σ lines, or the invoice amount when a receipt has no lines yet
    boolean overLimit,      // expenses only
    boolean hasOverride,    // a line amount has already been overridden
    Instant submittedAt
) {
}
