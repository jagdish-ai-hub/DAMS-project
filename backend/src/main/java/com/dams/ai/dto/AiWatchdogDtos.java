package com.dams.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Watchdog answers (FEAT-11 anomalies, FEAT-16 risk scores, FEAT-13 query roots).
 * All rows are deterministic rules over the override audit + review queues — the
 * language layer only explains them, never invents them.
 */
public final class AiWatchdogDtos {

    private AiWatchdogDtos() {
    }

    public record AnomalyItem(
        String kind,        // "override-cluster" | "near-limit" | "after-hours" | "cash-variance" | "unclosed-day"
        String severity,    // "info" | "watch" | "urgent"
        String message,
        String branchCode,
        String documentNo
    ) {
    }

    /** Pre-approval health of one SUBMITTED document (FEAT-16). Read-only for Owner. */
    public record RiskScore(
        String type,        // "receipt" | "expense"
        Long id,
        String documentNo,
        String branchCode,
        int score,          // 0 (clean) .. 100 (needs a careful look)
        List<String> reasons
    ) {
    }

    /** Why entries keep coming back queried (FEAT-13) — one cluster per root cause. */
    public record QueryRoot(
        String cause,
        long count,
        String suggestion
    ) {
    }
}
