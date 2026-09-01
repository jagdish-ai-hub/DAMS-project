package com.dams.expense.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One expense line as entered on the form or the Add Expense modal. */
@Getter
@Setter
@NoArgsConstructor
public class ExpenseLineInput {

    @NotNull(message = "transactionDate is required")
    private LocalDate transactionDate;

    @NotNull(message = "subCategoryId is required")
    private Long subCategoryId;

    @NotNull(message = "expenseModeId is required")
    private Long expenseModeId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be greater than 0")
    private BigDecimal amount;

    private Long bankId;

    @Size(max = 80, message = "Transaction reference must be at most 80 characters")
    private String transactionRef;

    @Size(max = 300, message = "Remark must be at most 300 characters")
    private String remark;
}
