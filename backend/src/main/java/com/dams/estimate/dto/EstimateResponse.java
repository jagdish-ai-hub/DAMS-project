package com.dams.estimate.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EstimateResponse(
    Long id,
    Long jobCardId,
    String status,
    BigDecimal total,
    List<EstimateLineResponse> lines,
    // Variance vs the final invoice (null until the job card is billed).
    BigDecimal invoiceAmount,
    BigDecimal varianceVsInvoice,
    Long approvedBy,
    Instant decidedAt,
    String decisionNote,
    Long createdBy,
    Instant createdAt
) {
    public record EstimateLineResponse(int lineNo, String description, BigDecimal amount) {
    }
}
