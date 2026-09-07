package com.dams.budget.controller;

import com.dams.budget.dto.BudgetResponse;
import com.dams.budget.dto.BudgetUpsertRequest;
import com.dams.budget.service.BudgetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Monthly spend caps per expense category. Reads are open to any authenticated org user
 * (the dashboard / review screens compare spend against the cap); writes are OWNER-only.
 * Caps never block a submission — they only inform.
 */
@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets", description = "Owner: monthly spend caps per expense category")
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    @Operation(summary = "List budgets, optionally for one month (?month=YYYYMM)")
    public List<BudgetResponse> list(@RequestParam(name = "month", required = false) String monthKey) {
        return budgetService.list(monthKey);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(summary = "Create or move a monthly cap (upsert on category + month)")
    public BudgetResponse upsert(@Valid @RequestBody BudgetUpsertRequest request) {
        return budgetService.upsert(request);
    }
}
