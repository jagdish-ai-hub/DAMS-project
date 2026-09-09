package com.dams.cash.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A recorded end-of-day cash close. */
public record CashDayCloseResponse(
    Long id,
    Long branchId,
    String branchCode,
    LocalDate closeDate,
    BigDecimal openingAmount,
    BigDecimal computedClosing,
    BigDecimal countedAmount,
    BigDecimal variance,             // countedAmount − computedClosing
    String varianceRemark,
    Long closedBy,
    String closedByName,
    Instant closedAt
) {
}
