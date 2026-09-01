package com.dams.cash.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The cashier closes a branch's cash for a date: the physically counted amount, and a
 * remark that is mandatory when the counted amount differs from the computed position.
 */
@Getter
@Setter
@NoArgsConstructor
public class CloseDayRequest {

    /** Optional — defaults to the cashier's home branch. */
    private Long branchId;

    @NotNull(message = "closeDate is required")
    private LocalDate closeDate;

    @NotNull(message = "countedAmount is required")
    @PositiveOrZero(message = "countedAmount cannot be negative")
    private BigDecimal countedAmount;

    @Size(max = 300, message = "Variance remark must be at most 300 characters")
    private String varianceRemark;
}
