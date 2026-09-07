package com.dams.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Operations answers (FEAT-12 claim chaser, FEAT-15 cash advice, FEAT-21 close
 * checklist). Built from claim aging + drawer math + queue counts the app already
 * computes — never a second implementation of the money logic.
 */
public final class AiOpsDtos {

    private AiOpsDtos() {
    }

    /** One at-risk Warranty/AMC/CG claim with a ready follow-up draft (FEAT-12). */
    public record ClaimInsight(
        String documentNo,
        String branchCode,
        String customerName,
        BigDecimal amount,
        int ageDays,
        String bucket,          // "0-30" | "31-60" | "61-90" | "90+"
        String draftFollowUp
    ) {
    }

    /** Deposit timing + variance explanation per branch (FEAT-15). */
    public record CashAdvice(
        String branchCode,
        String advice,
        String severity           // "info" | "watch" | "urgent"
    ) {
    }

    /** Month-end sign-off checklist, one row per branch (FEAT-21). */
    public record CloseChecklistRow(
        String branchCode,
        boolean cashDaysClosed,
        String cashNote,
        long pendingReview,
        long openClaims,
        boolean readyToClose
    ) {
    }
}
