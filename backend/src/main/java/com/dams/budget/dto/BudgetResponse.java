package com.dams.budget.dto;

import com.dams.budget.entity.ExpenseBudget;

import java.math.BigDecimal;

/** One monthly cap for one expense category. */
public record BudgetResponse(
    Long id,
    Long categoryId,
    String monthKey,
    BigDecimal capAmount
) {

    public static BudgetResponse of(ExpenseBudget b) {
        return new BudgetResponse(b.getId(), b.getCategoryId(), b.getMonthKey(), b.getCapAmount());
    }
}
