package com.dams.ledger.dto;

import com.dams.customer.dto.CustomerHistoryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Shareable customer statement (FEAT-43): what a fleet owner asks for — dues,
 * payments and balance across every vehicle, printable or WhatsApp-shared.
 * The figures come straight from the customer history card; this wraps them
 * with statement metadata so the renderer never recomputes money.
 */
public record CustomerStatementResponse(
    Long customerId,
    String customerName,
    String customerPhone,
    LocalDate generatedOn,
    String generatedBy,
    BigDecimal totalInvoiced,
    BigDecimal totalReceived,
    BigDecimal balanceDue,
    java.util.List<CustomerHistoryResponse.JobCardSummary> jobCards,
    java.util.List<CustomerHistoryResponse.TimelineEntry> payments
) {
}
