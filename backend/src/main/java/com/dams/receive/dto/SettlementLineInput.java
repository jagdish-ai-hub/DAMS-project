package com.dams.receive.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One settlement line as entered on the form or the Add Payment modal. */
@Getter
@Setter
@NoArgsConstructor
public class SettlementLineInput {

    @NotNull(message = "transactionDate is required")
    private LocalDate transactionDate;

    @NotNull(message = "settlementModeId is required")
    private Long settlementModeId;

    /**
     * A plain line must be > 0; a Warranty/AMC/CGW claim line may be 0 (see
     * {@code ReceiveDocumentService.applyLineInput}, which enforces that distinction —
     * this annotation only blocks a negative amount at the boundary).
     */
    @NotNull(message = "amount is required")
    @PositiveOrZero(message = "amount cannot be negative")
    private BigDecimal amount;

    private Long bankId;

    @Size(max = 80, message = "Transaction reference must be at most 80 characters")
    private String transactionRef;

    @Size(max = 300, message = "Remark must be at most 300 characters")
    private String remark;
}
