package com.dams.expense.entity;

/**
 * Finance Manager pre-approval of an over-limit expense (rev 53) — a small state machine that
 * runs while the document is still DRAFT, before it ever enters the review workflow.
 * {@code null} on the document means approval was never requested.
 */
public enum PreApprovalStatus {
    /** Sent for review; waiting for the FM. The draft is locked for the Cashier. */
    PENDING,
    /** FM approved; the Cashier may Submit as long as the total stays within the approved amount. */
    APPROVED,
    /** FM sent it back with a note; the Cashier edits it and sends it for review again. */
    QUERIED
}
