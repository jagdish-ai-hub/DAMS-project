package com.dams.receive.dto;

import com.dams.audit.dto.DocumentHistoryEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A receive document with the job-card facts a screen needs joined in (category, business
 * status, invoice, plus the derived Pending Amount and claim-close flags), the settlement
 * lines, and this document's own received total.
 */
public record ReceiveDocumentResponse(
    Long id,
    String documentNo,               // null until submitted
    String workflowStatus,
    boolean settled,

    Long jobCardId,
    String jobCardReference,         // {branchCode}-JC-{id}
    Long branchId,
    String branchCode,
    String branchName,

    Long customerId,
    String customerName,
    String customerPhone,
    String vehicleNo,

    String dbmId,
    String invoiceNo,
    BigDecimal invoiceAmount,
    boolean b2b,
    String gstNo,

    Long categoryId,
    String categoryName,
    boolean isClaim,
    Long businessStatusId,
    String businessStatusName,

    BigDecimal pendingAmount,        // job-card-wide; 0 when no invoice or a claim is closed
    boolean settledViaClaimClose,
    BigDecimal claimFinalAmount,
    boolean claimOverridden,        // final amount differs from what was received ("Overridden · Final")
    String claimOverrideReason,
    BigDecimal totalReceived,        // Σ this document's lines
    boolean canRecordPayment,

    Long createdBy,
    String createdByName,
    Long lastModifiedBy,
    Instant createdAt,
    Instant submittedAt,

    List<SettlementLineResponse> lines,
    List<DocumentHistoryEntry> history   // oldest-first; drives the review pane + the cashier's "why queried"
) {
}
