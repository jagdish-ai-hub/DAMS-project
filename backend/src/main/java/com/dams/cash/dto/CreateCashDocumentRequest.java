package com.dams.cash.dto;

import com.dams.cash.entity.CashDirection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One IN-from-bank or OUT-to-bank cash movement. Single amount, no sub-lines. No branch
 * field — it posts under the cashier's home branch. {@code submit = true} also submits it
 * (assigns the {@code C} number, moves it to SUBMITTED).
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateCashDocumentRequest {

    @NotNull(message = "direction is required (IN or OUT)")
    private CashDirection direction;

    @NotNull(message = "transactionDate is required")
    private LocalDate transactionDate;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than 0")
    private BigDecimal amount;

    private Long bankId;

    @Size(max = 80, message = "Transaction reference must be at most 80 characters")
    private String transactionRef;

    @Size(max = 300, message = "Remark must be at most 300 characters")
    private String remark;

    private boolean submit;
}
