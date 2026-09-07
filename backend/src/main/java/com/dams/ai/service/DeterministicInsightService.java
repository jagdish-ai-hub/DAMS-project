package com.dams.ai.service;

import org.springframework.stereotype.Service;

/**
 * Built-in {@link InsightService}: deterministic wording with zero network calls.
 * Used in v1 and in tests so every AI endpoint works offline against Neon; swap in
 * a hosted-LLM implementation later without changing any controller or service.
 */
@Service
public class DeterministicInsightService implements InsightService {

    @Override
    public String phraseAnswer(String question, String facts) {
        if (facts == null || facts.isBlank()) {
            return "I could not find anything for that in this organization. "
                + "Try a branch code, a document number like OOR-JUL26-R-021, or a customer name.";
        }
        return facts;
    }

    @Override
    public String phraseBrief(String facts) {
        if (facts == null || facts.isBlank()) {
            return "No activity in this scope yet.";
        }
        return facts;
    }
}
