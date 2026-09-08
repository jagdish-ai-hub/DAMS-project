package com.dams.followup.dto;

import com.dams.followup.entity.CreditFollowup;

import java.time.Instant;
import java.time.LocalDate;

public record FollowupResponse(
    Long id,
    Long receiveDocumentId,
    String documentNo,
    String customerName,
    String customerPhone,
    Long branchId,
    String branchCode,
    LocalDate dueDate,
    String promiseNote,
    String status,
    boolean overdue,
    long daysOverdue,
    java.math.BigDecimal pendingAmount,
    int remindedCount,
    Instant lastRemindedAt,
    Long createdBy,
    Instant createdAt
) {
    public static FollowupResponse of(CreditFollowup f, String documentNo, String customerName,
                                      String customerPhone, Long branchId, String branchCode,
                                      boolean overdue, long daysOverdue, java.math.BigDecimal pendingAmount) {
        return new FollowupResponse(
            f.getId(), f.getReceiveDocumentId(), documentNo, customerName, customerPhone,
            branchId, branchCode, f.getDueDate(), f.getPromiseNote(), f.getStatus(),
            overdue, daysOverdue, pendingAmount, f.getRemindedCount(), f.getLastRemindedAt(),
            f.getCreatedBy(), f.getCreatedAt());
    }
}
