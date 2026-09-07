package com.dams.myentries.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row in the cashier's "My Entries" list — a receipt ({@code kind = "RECEIPT"}), an
 * expense ({@code kind = "EXPENSE"}) or a cash movement ({@code kind = "CASH"}), newest first. {@code queried} rows are highlighted and
 * open in edit mode for fix-and-resubmit (AGENT.md decision #8).
 *
 * {@code total} is the document's own line total (received, or paid out). {@code pendingAmount}
 * is the job-card-wide Pending Amount for a receipt, and null for an expense (nothing
 * aggregates that way on the expense side). {@code overLimit} is only ever true for an
 * expense with a line over its sub-category limit.
 */
public record MyEntryResponse(
    Long id,
    String kind,                // "RECEIPT" | "EXPENSE" | "CASH"
    String documentNo,          // null while still a draft
    String workflowStatus,
    boolean settled,            // receipts only; false for expenses
    boolean claimOverridden,    // receipts only: FM closed the claim at a different final amount
    Long jobCardId,             // null for a branch-overhead expense
    String jobCardReference,
    String partyName,           // customer (receipt) or receiver (expense)
    BigDecimal total,
    BigDecimal pendingAmount,   // null for an expense
    int lineCount,
    boolean overLimit,          // expenses only
    boolean today,
    boolean queried,
    Instant createdAt,
    Instant submittedAt
) {
}
