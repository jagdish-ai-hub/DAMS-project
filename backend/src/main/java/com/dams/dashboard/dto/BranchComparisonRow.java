package com.dams.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One branch's line in the comparison table. */
public record BranchComparisonRow(
    Long branchId,
    String branchCode,
    String branchName,
    BigDecimal collections,
    BigDecimal expenses,
    BigDecimal net,
    BigDecimal cashInHand,
    LocalDate lastClosed,     // latest cash_day_close date, or null
    BigDecimal variance,      // that close's variance, or null
    long pendingReview
) {
}
