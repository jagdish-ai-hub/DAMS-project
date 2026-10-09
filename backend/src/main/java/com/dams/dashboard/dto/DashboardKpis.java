package com.dams.dashboard.dto;

import java.math.BigDecimal;

/**
 * Headline numbers for the period. {@code collections} and {@code expenses} count APPROVED
 * documents only and exclude cash In/Out (AGENT.md decision #1); {@code cashInHand} is the
 * running drawer position as of today (last close + everything since, rev 71);
 * {@code pendingReview} is entries not yet APPROVED. {@code collectionsAwaiting} /
 * {@code expensesAwaiting} are the money on entries still in the workflow (submitted, verified or
 * queried) for the same period, so the approved-only headline never hides a pile waiting.
 */
public record DashboardKpis(
    BigDecimal collections,
    BigDecimal expenses,
    BigDecimal net,
    BigDecimal cashInHand,
    long pendingReview,
    BigDecimal collectionsAwaiting,
    BigDecimal expensesAwaiting
) {
}
