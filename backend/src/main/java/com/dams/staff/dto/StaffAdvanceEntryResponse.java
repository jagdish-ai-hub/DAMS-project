package com.dams.staff.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StaffAdvanceEntryResponse(
    Long id,
    Long staffId,
    String kind,
    BigDecimal amount,
    LocalDate txnDate,
    String note,
    Long createdBy,
    java.time.Instant createdAt
) {
    public static StaffAdvanceEntryResponse of(com.dams.staff.entity.StaffAdvanceEntry e) {
        return new StaffAdvanceEntryResponse(
            e.getId(), e.getStaffId(), e.getKind(), e.getAmount(),
            e.getTxnDate(), e.getNote(), e.getCreatedBy(), e.getCreatedAt());
    }
}
