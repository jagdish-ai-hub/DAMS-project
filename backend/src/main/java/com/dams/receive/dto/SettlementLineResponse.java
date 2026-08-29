package com.dams.receive.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One settlement line for display: resolved mode / bank names, the override trail, receipt count. */
public record SettlementLineResponse(
    Long id,
    Integer lineNo,
    String lineId,
    LocalDate transactionDate,
    Long settlementModeId,
    String settlementModeName,
    BigDecimal amount,
    BigDecimal originalAmount,
    Long bankId,
    String bankName,
    String transactionRef,
    String remark,
    boolean overridden,
    String overrideReason,
    Instant overriddenAt,
    int attachmentCount,
    Instant createdAt
) {
}
