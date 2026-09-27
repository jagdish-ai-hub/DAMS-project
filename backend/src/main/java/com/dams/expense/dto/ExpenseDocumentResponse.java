package com.dams.expense.dto;

import com.dams.audit.dto.DocumentHistoryEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * An expense document with the receiver and (optional) job-card facts a screen needs
 * joined in, the expense lines, the document total, and the flags that drive the form:
 * {@code overLimit} (any line over its limit) and {@code claimEligible} (the document sits
 * on a job card whose category is a claim category, so "Transfer to Claim" is offered).
 */
public record ExpenseDocumentResponse(
    Long id,
    String documentNo,               // null until submitted
    String workflowStatus,
    boolean overLimit,

    Long branchId,
    String branchCode,
    String branchName,

    Long jobCardId,                  // null for a branch-overhead expense
    String jobCardReference,         // {branchCode}-JC-{id}, or null

    Long receiverId,
    String receiverName,
    String receiverPhone,

    Long customerId,                 // from the job card, or null
    String customerName,             // the document's own value, else the job card's
    String vehicleNo,                // the document's own value, else the job card's
    String invoiceNo,
    String dbmId,

    Long expenseCategoryId,
    String expenseCategoryName,

    Long businessStatusId,
    String businessStatusName,
    boolean businessStatusTriggersClaim,
    boolean claimEligible,

    BigDecimal totalAmount,          // Σ this document's lines

    Long createdBy,
    String createdByName,
    Long lastModifiedBy,
    Instant createdAt,
    Instant submittedAt,

    // FM pre-approval of an over-limit expense (rev 53). preApprovalStatus is null when never
    // requested, else PENDING | APPROVED | QUERIED. preApprovalCovers = approved and the total
    // is still within the approved amount — the Cashier may Submit, the Accountant may Close.
    String preApprovalStatus,
    BigDecimal preApprovedAmount,
    String preApprovedByName,
    Instant preApprovedAt,
    Instant approvalRequestedAt,
    boolean preApprovalCovers,
    // rev 54 — FM approval is needed for this expense: over a limit, or its status is flagged.
    boolean needsFmApproval,

    List<ExpenseLineResponse> lines,
    List<DocumentHistoryEntry> history   // oldest-first; drives the review pane + the cashier's "why queried"
) {
}
