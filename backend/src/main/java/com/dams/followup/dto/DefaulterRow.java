package com.dams.followup.dto;

import java.math.BigDecimal;

/** One customer ranked by total outstanding across open follow-ups (FEAT-35 defaulter view). */
public record DefaulterRow(
    Long customerId,
    String customerName,
    String customerPhone,
    BigDecimal totalOutstanding,
    int openFollowups,
    int overdueFollowups,
    Long oldestOverdueDays
) {
}
