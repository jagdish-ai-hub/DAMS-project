package com.dams.dashboard.dto;

import java.util.List;

/** Everything the dashboard's top half needs, for a branch scope and a period. */
public record DashboardSummary(
    String scope,                 // branch code, or "ALL"
    String period,                // "today" | "mtd"
    DashboardKpis kpis,
    List<TrendPoint> trend,
    List<NamedAmount> byMode,      // collections by settlement mode (donut)
    List<NamedAmount> byCategory,  // expenses by category (bars)
    List<BranchComparisonRow> branchComparison
) {
}
