package com.dams.staff.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Record an ADVANCE out or a RECOVERY in. Entries are never edited or deleted. */
@Getter
@Setter
@NoArgsConstructor
public class CreateAdvanceEntryRequest {

    @NotNull(message = "kind is required (ADVANCE or RECOVERY)")
    private String kind;

    @NotNull(message = "amount is required")
    private BigDecimal amount;

    @NotNull(message = "txnDate is required")
    private LocalDate txnDate;

    @Size(max = 500, message = "Note must be at most 500 characters")
    private String note;
}
