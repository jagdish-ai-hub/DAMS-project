package com.dams.jobcard.dto;

import java.time.LocalDate;

public record RenewalRow(
    Long jobCardId,
    String reference,
    Long branchId,
    String branchCode,
    String customerName,
    String customerPhone,
    String vehicleNo,
    LocalDate serviceDueDate,
    long daysUntilDue
) {
}
