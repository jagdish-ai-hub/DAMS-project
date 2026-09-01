package com.dams.cash.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The Accountant sets a branch's first-ever drawer opening. One per branch, ever — after
 * that, each day's opening is the previous day's counted close.
 */
@Getter
@Setter
@NoArgsConstructor
public class CashOpeningRequest {

    @NotNull(message = "branchId is required")
    private Long branchId;

    @NotNull(message = "openingDate is required")
    private LocalDate openingDate;

    @NotNull(message = "amount is required")
    @PositiveOrZero(message = "amount cannot be negative")
    private BigDecimal amount;
}
