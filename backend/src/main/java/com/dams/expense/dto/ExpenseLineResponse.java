package com.dams.expense.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One expense line for display: resolved sub-category / mode / bank names, the sub-category
 * limit and whether this line is over it, the override trail, and the receipt count.
 */
public record ExpenseLineResponse(
    Long id,
    Integer lineNo,
    String lineId,
    LocalDate transactionDate,
    Long subCategoryId,
    String subCategoryName,
    BigDecimal limitAmount,          // sub-category soft limit; null = no limit
    boolean overLimit,               // amount > limitAmount
    Long expenseModeId,
    String expenseModeName,
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
