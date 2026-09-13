package com.dams.customer.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One expense tagged to a customer's job card — the "Expenses" section on the customer
 * history card, fetched on demand ({@code GET /customers/{id}/expenses}) rather than
 * bundled into {@link CustomerHistoryResponse}, since most look-ups never need it.
 */
public record CustomerExpenseEntry(
    Long id,
    String documentNo,
    Long jobCardId,
    String jobCardReference,
    String branchCode,
    String categoryName,
    String receiverName,
    BigDecimal amount,
    String workflowStatus,
    Instant date
) {}
