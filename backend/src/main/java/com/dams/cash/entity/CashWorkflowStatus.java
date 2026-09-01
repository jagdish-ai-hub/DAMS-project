package com.dams.cash.entity;

/**
 * The review lifecycle of a {@link CashDocument} — the same maker-checker chain every other
 * document type has (AGENT.md decision #1: "Same maker-checker workflow ... as every other
 * document"). Stage 6 drives the cashier side (DRAFT → SUBMITTED, QUERIED → SUBMITTED via
 * resubmit); the Accountant verify / query / reject and the FM approve transitions are in
 * Stage 8.5 ({@link com.dams.review.service.ReviewService}).
 */
public enum CashWorkflowStatus {
    DRAFT,
    SUBMITTED,
    VERIFIED,
    APPROVED,
    QUERIED,
    REJECTED
}
