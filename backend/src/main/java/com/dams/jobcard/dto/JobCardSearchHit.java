package com.dams.jobcard.dto;

import java.time.Instant;

/**
 * One row of the job-card picker (rev 56) -- just what a dropdown needs, so a keystroke does
 * not pay for the full {@link JobCardResponse} (pending amount, claim close, ...).
 * {@code customerId} is null for a job card opened from an Expense that has no customer yet;
 * {@code vehicleNo} then falls back to the typed-only number.
 */
public record JobCardSearchHit(
    Long id,
    String reference,          // {branchCode}-JC-{id}
    Long branchId,
    String branchCode,
    Long customerId,
    String customerName,
    Long vehicleId,
    String vehicleNo,
    String dbmId,
    String invoiceNo,
    Long categoryId,
    Instant createdAt
) {
}
