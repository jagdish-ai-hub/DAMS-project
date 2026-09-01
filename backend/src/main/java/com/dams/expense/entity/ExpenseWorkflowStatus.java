package com.dams.expense.entity;

/**
 * The review lifecycle of an {@link ExpenseDocument}. Distinct from the document's business
 * status and from {@code over_limit} — none is derived from the others.
 *
 * Stage 5 drives the cashier side (DRAFT → SUBMITTED, QUERIED → SUBMITTED via resubmit).
 * VERIFIED / APPROVED / QUERIED / REJECTED are added in Stages 7–8. CLOSED is the
 * Accountant's terminal close (Stage 7); this stage only reads it — a closed document
 * accepts no more lines.
 */
public enum ExpenseWorkflowStatus {
    DRAFT,
    SUBMITTED,
    VERIFIED,
    APPROVED,
    QUERIED,
    REJECTED,
    CLOSED
}
