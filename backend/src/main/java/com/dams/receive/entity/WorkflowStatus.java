package com.dams.receive.entity;

/**
 * The review lifecycle of a {@link ReceiveDocument}. Distinct from the job card's
 * business status and from the computed {@code settled} flag — the three are never derived
 * from each other (plan.md "Business status vs workflow status").
 *
 * Stage 4 drives the cashier side (DRAFT → SUBMITTED, QUERIED → SUBMITTED via resubmit).
 * VERIFIED / APPROVED / QUERIED / REJECTED transitions are added in Stages 7–8.
 *
 * {@code FM_QUERIED} (rev 49) is a second, distinct query state: the Accountant's query on a
 * SUBMITTED document still yields {@code QUERIED} (fix-and-resubmit goes to the Cashier), but
 * the Finance Manager's query on a VERIFIED document yields {@code FM_QUERIED} instead — routed
 * back to the Accountant's own queue, fixed there with the same override tools, and resubmitted
 * straight back to VERIFIED (skipping the Cashier).
 */
public enum WorkflowStatus {
    DRAFT,
    SUBMITTED,
    VERIFIED,
    APPROVED,
    QUERIED,
    FM_QUERIED,
    REJECTED
}
