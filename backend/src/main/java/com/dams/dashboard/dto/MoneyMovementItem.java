package com.dams.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One line of money movement for a reconciliation breakdown — a settlement line (receipt) or
 * an expense line. Used behind the Owner Dashboard's Collections/Expenses cards and the Cash
 * page's "cash receipts" / "cash expenses" drawer lines, so clicking a KPI always shows
 * exactly the rows that sum to it.
 */
public record MoneyMovementItem(
    String kind,              // "receipt" | "expense"
    Long documentId,
    String documentNo,        // null until the document is submitted
    String workflowStatus,
    LocalDate date,            // the line's transaction_date
    Instant createdAt,
    String branchCode,
    String party,              // customer name (receipt) / receiver name (expense)
    String description,        // job card reference (receipt) / category name (expense)
    String modeName,
    BigDecimal amount
) {
}
