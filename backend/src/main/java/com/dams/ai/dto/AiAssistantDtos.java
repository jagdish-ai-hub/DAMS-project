package com.dams.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Answers for the scoped Owner/Admin assistant (FEAT-09 Ask DAMS, FEAT-10 brief,
 * FEAT-14 benchmark). Numbers always come from the read-only aggregates
 * ({@code DashboardService}); the phrasing layer only words them.
 */
public final class AiAssistantDtos {

    private AiAssistantDtos() {
    }

    /** One grounded answer: text plus the real doc numbers it cites (never invented). */
    public record AiAnswer(
        String answer,
        List<String> citedDocs,
        String requestId
    ) {
    }

    /** The 30-second morning brief (FEAT-10). Cash In/Out excluded from money lines. */
    public record AiBrief(
        String period,
        String scope,
        BigDecimal collections,
        BigDecimal expenses,
        BigDecimal net,
        BigDecimal cashInHand,
        long pendingReview,
        List<String> bullets
    ) {
    }

    /** Plain-English branch comparison (FEAT-14) — one line per branch, best first. */
    public record BenchmarkNarrative(
        List<String> lines,
        String headline
    ) {
    }
}
