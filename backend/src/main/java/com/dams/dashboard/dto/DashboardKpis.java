package com.dams.dashboard.dto;

import java.math.BigDecimal;

/**
 * Headline numbers for the period. {@code collections} and {@code expenses} count APPROVED
 * documents only and exclude cash In/Out (AGENT.md decision #1); {@code cashInHand} is the
 * live drawer position today; {@code pendingReview} is entries not yet APPROVED.
 */
public record DashboardKpis(
    BigDecimal collections,
    BigDecimal expenses,
    BigDecimal net,
    BigDecimal cashInHand,
    long pendingReview
) {
}
