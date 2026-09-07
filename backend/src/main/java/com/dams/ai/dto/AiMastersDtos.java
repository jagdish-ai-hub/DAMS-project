package com.dams.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Masters answers (FEAT-17 receiver duplicates, FEAT-18 masters health, FEAT-19
 * limit advice, FEAT-20 smart search). Suggestions only — writes still go through
 * the existing Owner {@code PATCH /masters} / receiver endpoints (deactivate, never delete).
 */
public final class AiMastersDtos {

    private AiMastersDtos() {
    }

    public record ReceiverDuplicate(
        Long firstId,
        String firstName,
        Long secondId,
        String secondName,
        String reason
    ) {
    }

    public record MastersHealthItem(
        String list,
        String name,
        String issue,           // "unused-90d" | "duplicate-name" | "inactive-still-referenced"
        String suggestion
    ) {
    }

    public record LimitAdvice(
        String subCategory,
        BigDecimal limitAmount,
        long breaches,
        String suggestion
    ) {
    }

    /** Fuzzy + intent-ranked search (FEAT-20) — same hit shape, AI-ordered. */
    public record SmartSearchResponse(
        String query,
        String normalized,
        String intent,          // "unpaid" | "claim" | "recent" | "general"
        List<SmartHit> hits
    ) {
    }

    public record SmartHit(
        Long customerId,
        String customerName,
        String phone,
        BigDecimal totalOutstanding,
        String matchField,
        String whyRank
    ) {
    }
}
