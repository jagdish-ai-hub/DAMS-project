package com.dams.cash.dto;

import com.dams.cash.entity.CashCloseReopenRequest;
import com.dams.cash.entity.ReopenRequestStatus;

import java.time.Instant;
import java.time.LocalDate;

/** One reopen request, with the branch code for display. */
public record ReopenRequestResponse(
    Long id,
    Long branchId,
    String branchCode,
    LocalDate closeDate,
    String reason,
    ReopenRequestStatus status,
    Long requestedBy,
    Long decidedBy,
    String decisionNote,
    Instant decidedAt,
    Instant createdAt
) {

    public static ReopenRequestResponse of(CashCloseReopenRequest r, String branchCode) {
        return new ReopenRequestResponse(r.getId(), r.getBranchId(), branchCode, r.getCloseDate(),
            r.getReason(), r.getStatus(), r.getRequestedBy(), r.getDecidedBy(),
            r.getDecisionNote(), r.getDecidedAt(), r.getCreatedAt());
    }
}
