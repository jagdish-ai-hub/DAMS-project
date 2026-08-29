package com.dams.customer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The customer history card from cashier-home.html: identity, roll-up totals, the job-card
 * list, and a payment timeline.
 *
 * From Stage 4 the money fields are real: {@code received} is the sum of settlement lines
 * across all of a job card's receive documents, {@code balance} is the job-card-wide Pending
 * Amount, {@code workflowStatus} is the open (or most recent) document's status, and
 * {@code timeline} lists every settlement line newest-first.
 */
public record CustomerHistoryResponse(
    Long customerId,
    String customerName,
    String phone,
    List<Vehicle> vehicles,
    int jobCardCount,
    BigDecimal totalInvoiced,
    BigDecimal totalReceived,
    BigDecimal totalOutstanding,
    List<JobCardSummary> jobCards,
    List<TimelineEntry> timeline
) {
    public record Vehicle(Long id, String vehicleNo) {}

    public record JobCardSummary(
        Long id,
        String reference,          // {branchCode}-JC-{id}
        String branchCode,
        String branchName,
        String categoryName,
        boolean isClaim,
        String businessStatusName,
        String dbmId,
        String invoiceNo,
        BigDecimal invoiceAmount,
        BigDecimal received,
        BigDecimal balance,
        String workflowStatus,       // open (or most recent) receive document's status; null if none
        boolean canRecordPayment,    // cashier, own branch, open balance
        Long receiveDocumentId,      // the open document's id, else the most recent, else null
        boolean receiveDocumentSettled,
        Instant createdAt
    ) {}

    public record TimelineEntry(
        Instant date,
        String description,
        String lineId,
        String mode,
        BigDecimal amount
    ) {}
}
