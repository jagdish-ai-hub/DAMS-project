package com.dams.ai.service;

/**
 * Phrasing layer behind the Owner/Admin assistant (FEAT-09..FEAT-21).
 *
 * Kept as an interface — like {@code StorageService} for R2 — so a hosted LLM can
 * replace the deterministic built-in without touching business logic. The contract:
 * implementations may word and order facts, but must never invent document numbers,
 * amounts, or branch codes — every fact arrives in {@code facts} from the read-only
 * aggregates.
 */
public interface InsightService {

    /** Word a grounded answer from pre-fetched facts (no data access inside). */
    String phraseAnswer(String question, String facts);

    /** Join insight bullets into the morning-brief paragraph style. */
    String phraseBrief(String facts);
}
