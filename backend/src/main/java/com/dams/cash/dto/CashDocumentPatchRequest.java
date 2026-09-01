package com.dams.cash.dto;

import com.dams.cash.entity.CashDirection;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Edits allowed while a cash document is still DRAFT or QUERIED (the fix-and-resubmit path).
 * Every field is optional; only the non-null ones are applied.
 */
@Getter
@Setter
@NoArgsConstructor
public class CashDocumentPatchRequest {

    private CashDirection direction;
    private LocalDate transactionDate;

    @Positive(message = "amount must be greater than 0")
    private BigDecimal amount;

    private Long bankId;

    @Size(max = 80, message = "Transaction reference must be at most 80 characters")
    private String transactionRef;

    @Size(max = 300, message = "Remark must be at most 300 characters")
    private String remark;
}
