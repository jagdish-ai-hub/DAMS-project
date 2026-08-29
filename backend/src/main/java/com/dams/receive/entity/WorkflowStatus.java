package com.dams.receive.entity;

/**
 * The review lifecycle of a {@link ReceiveDocument}. Distinct from the job card's
 * business status and from the computed {@code settled} flag — the three are never derived
 * from each other (plan.md "Business status vs workflow status").
 *
 * Stage 4 drives the cashier side (DRAFT → SUBMITTED, QUERIED → SUBMITTED via resubmit).
 * VERIFIED / APPROVED / QUERIED / REJECTED transitions are added in Stages 7–8.
 */
public enum WorkflowStatus {
    DRAFT,
    SUBMITTED,
    VERIFIED,
    APPROVED,
    QUERIED,
    REJECTED
}
