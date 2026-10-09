package com.dams.dashboard.controller;

import com.dams.dashboard.dto.ActivityItem;
import com.dams.dashboard.dto.ClaimsSummary;
import com.dams.dashboard.dto.DashboardSummary;
import com.dams.dashboard.dto.MoneyMovementItem;
import com.dams.dashboard.dto.OutstandingItem;
import com.dams.dashboard.dto.PendingWork;
import com.dams.dashboard.service.ClaimsSummaryService;
import com.dams.dashboard.service.PendingWorkService;
import com.dams.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Owner dashboard — read-only org aggregates. Owner and Finance Manager only. Everything is
 * scoped by an optional {@code branchId} (omit for the whole org) and, for the summary, a
 * {@code period} of {@code today} or {@code mtd} (month-to-date, the default).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Owner dashboard aggregates")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ClaimsSummaryService claimsSummaryService;
    private final PendingWorkService pendingWorkService;

    public DashboardController(DashboardService dashboardService, ClaimsSummaryService claimsSummaryService,
                               PendingWorkService pendingWorkService) {
        this.dashboardService = dashboardService;
        this.claimsSummaryService = claimsSummaryService;
        this.pendingWorkService = pendingWorkService;
    }

    @GetMapping("/summary")
    @Operation(summary = "KPIs, trend, by-mode / by-category breakdowns, branch comparison")
    public DashboardSummary summary(@RequestParam(required = false) Long branchId,
                                    @RequestParam(required = false, defaultValue = "mtd") String period) {
        return dashboardService.summary(branchId, period);
    }

    @GetMapping("/claims")
    @Operation(summary = "Claims at a glance — claimed / received / rejected / pending, expense + receipt claims (rev 62)")
    public ClaimsSummary claims(@RequestParam(required = false) Long branchId,
                                @RequestParam(required = false, defaultValue = "mtd") String period) {
        return claimsSummaryService.summary(branchId, period);
    }

    @GetMapping("/outstanding")
    @Operation(summary = "Money still owed / awaiting — part-paid jobs, B2B credit, open claims")
    public List<OutstandingItem> outstanding(@RequestParam(required = false) Long branchId) {
        return dashboardService.outstanding(branchId);
    }

    @GetMapping("/collections-breakdown")
    @Operation(summary = "Line-level receipts behind the Collections KPI, for the same period/branch")
    public List<MoneyMovementItem> collectionsBreakdown(@RequestParam(required = false) Long branchId,
                                                         @RequestParam(required = false, defaultValue = "mtd") String period) {
        return dashboardService.collectionsBreakdown(branchId, period);
    }

    @GetMapping("/expenses-breakdown")
    @Operation(summary = "Line-level expenses behind the Expenses KPI, for the same period/branch")
    public List<MoneyMovementItem> expensesBreakdown(@RequestParam(required = false) Long branchId,
                                                      @RequestParam(required = false, defaultValue = "mtd") String period) {
        return dashboardService.expensesBreakdown(branchId, period);
    }

    @GetMapping("/cash-breakdown")
    @Operation(summary = "Movements behind the Cash in hand KPI — opening plus everything since the last close (rev 71)")
    public List<MoneyMovementItem> cashBreakdown(@RequestParam(required = false) Long branchId) {
        return dashboardService.cashBreakdown(branchId);
    }

    @GetMapping("/pending-work")
    @Operation(summary = "Entries waiting on the Cashier / Accountant / Finance Manager, with details (rev 71)")
    public PendingWork pendingWork(@RequestParam(required = false) Long branchId) {
        return pendingWorkService.summary(branchId);
    }

    @GetMapping("/activity")
    @Operation(summary = "Recent activity feed across the organisation")
    public List<ActivityItem> activity(@RequestParam(required = false) Long branchId,
                                       @RequestParam(required = false, defaultValue = "20") int limit) {
        return dashboardService.activity(branchId, limit);
    }
}
