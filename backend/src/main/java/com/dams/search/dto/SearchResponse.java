package com.dams.search.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Universal search result — one hit per matching customer, in the shape the cashier-home
 * mockup renders (name, vehicles, job-card count, a roll-up figure, and which field
 * matched).
 *
 * {@code totalOutstanding} is the sum of the customer's Pending Amounts across their
 * branch-visible job cards — the figure the mockup's result row shows.
 */
public record SearchResponse(
    String query,
    List<Hit> hits
) {
    public record Hit(
        Long customerId,
        String customerName,
        String phone,
        List<String> vehicles,
        int jobCardCount,
        BigDecimal totalInvoiced,
        BigDecimal totalOutstanding,
        String matchField        // "Name" | "Phone" | "Vehicle" | "Job Card" | "Invoice" | "Receipt" | "Expense"
    ) {}
}
