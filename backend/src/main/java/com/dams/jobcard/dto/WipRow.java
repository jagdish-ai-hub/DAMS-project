package com.dams.jobcard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WipRow(
    Long jobCardId,
    String reference,
    Long branchId,
    String branchCode,
    String customerName,
    String vehicleNo,
    String categoryName,
    String businessStatusName,
    LocalDate openedDate,
    long ageDays,
    String stuckReason,
    BigDecimal pendingAmount
) {
}
