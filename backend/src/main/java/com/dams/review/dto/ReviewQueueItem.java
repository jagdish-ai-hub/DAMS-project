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
    Instant submittedAt,
    String workflowStatus,  // SUBMITTED | VERIFIED | APPROVED | CLOSED | FM_QUERIED — lets a
                            // "verified" list still show each row's real state
    boolean isClaim,        // receipts only — job card carries a claim_type_id
    boolean isCashEligible, // receipts only — the direct-approve rule: no claim, status isn't
                            // "Credit", every settlement line is cash-mode (rev 49's
                            // Cash/Credit/Claim Transaction split reuses this same predicate)
    boolean preApproved,    // expenses only (rev 53) — FM pre-approved it and the total is still
                            // within the approved amount: the Accountant may close it without
                            // a second FM approval, and it stays off the FM's approval list
    Instant approvalRequestedAt // expenses only (rev 53) — when the Cashier sent it for review
) {
}
