package com.dams.jobcard.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A job card with everything a screen needs: the derived {@code reference}, the resolved
 * customer / vehicle / branch / category / status names, and the derived money fields.
 *
 * {@code pendingAmount} = invoice − Σ settlement lines across all of the job card's receive
 * documents, floored at 0; it is 0 when there is no invoice, and 0 once a ClaimClose exists
 * (then {@code settledViaClaimClose} is true and {@code claimFinalAmount} is set).
 *
 * {@code canRecordPayment} is a UI hint: true only when the caller is a CASHIER whose home
 * branch is this job card's branch, and there is an open balance to settle. Cross-branch
 * cashiers can view the job card but cannot post payments against it (see AGENT.md #2).
 */
public record JobCardResponse(
    Long id,
    String reference,
    Long branchId,
    String branchCode,
    String branchName,
    Long customerId,
    String customerName,
    String customerPhone,
    Long vehicleId,
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
    BigDecimal pendingAmount,
    boolean settledViaClaimClose,
    BigDecimal claimFinalAmount,
    boolean canRecordPayment,
    Instant createdAt
) {
    public static String reference(String branchCode, Long id) {
        return branchCode + "-JC-" + id;
    }
}
