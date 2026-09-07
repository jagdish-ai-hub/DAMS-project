package com.dams.budget.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Upsert one monthly cap: the (category, month) pair is the natural key. */
@Getter
@Setter
@NoArgsConstructor
public class BudgetUpsertRequest {

    @NotNull(message = "categoryId is required")
    private Long categoryId;

    @NotNull(message = "monthKey is required (YYYYMM)")
    @Pattern(regexp = "^[0-9]{6}$", message = "monthKey must be YYYYMM (e.g. 202608)")
    private String monthKey;

    @NotNull(message = "capAmount is required")
    @Positive(message = "capAmount must be greater than zero")
    private BigDecimal capAmount;
}
