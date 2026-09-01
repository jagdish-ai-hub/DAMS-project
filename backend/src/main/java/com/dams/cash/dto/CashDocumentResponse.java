package com.dams.cash.dto;

import com.dams.audit.dto.DocumentHistoryEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** One cash movement for display, with the bank / branch names joined in. */
public record CashDocumentResponse(
    Long id,
    String documentNo,               // null until submitted
    String direction,                // IN | OUT
    LocalDate transactionDate,
    BigDecimal amount,
    Long bankId,
    String bankName,
    String transactionRef,
    String remark,
    String workflowStatus,
    Long branchId,
    String branchCode,
    String branchName,
    Long createdBy,
    String createdByName,
    Long lastModifiedBy,
    Instant createdAt,
    Instant submittedAt,
    List<DocumentHistoryEntry> history   // oldest-first; drives the review pane + the cashier's "why queried"
) {
}
