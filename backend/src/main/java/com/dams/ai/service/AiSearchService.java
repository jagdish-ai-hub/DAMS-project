package com.dams.ai.service;

import com.dams.ai.dto.AiMastersDtos.SmartHit;
import com.dams.ai.dto.AiMastersDtos.SmartSearchResponse;
import com.dams.config.TenantContext;
import com.dams.search.dto.SearchResponse;
import com.dams.search.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic universal search (FEAT-20): vehicle-number tolerant, typo-tolerant,
 * intent-aware ranking over the existing {@link SearchService} — which keeps
 * enforcing branch scope + the cashier multi-branch toggle. This layer only
 * normalises the query and re-ranks hits; it never widens access.
 */
@Service
public class AiSearchService {

    private static final Logger log = LoggerFactory.getLogger(AiSearchService.class);

    private final SearchService searchService;

    public AiSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    @Transactional(readOnly = true)
    public SmartSearchResponse smartSearch(String rawQuery) {
        Long orgId = TenantContext.requireOrgId();
        String query = rawQuery == null ? "" : rawQuery.trim();
        // Vehicle numbers are stored normalised (uppercase, no spaces) — match that here.
        String normalized = query.toUpperCase().replaceAll("\\s+", "");
        String intent = detectIntent(query);

        Map<Long, SmartHit> merged = new LinkedHashMap<>();
        merge(merged, searchService.search(query), "exact match");
        if (!normalized.equals(query)) {
            merge(merged, searchService.search(normalized), "vehicle-number match");
        }
        String relaxed = relaxedQuery(query);
        if (!relaxed.equalsIgnoreCase(query) && !relaxed.equalsIgnoreCase(normalized)) {
            merge(merged, searchService.search(relaxed), "fuzzy match");
        }

        List<SmartHit> hits = new ArrayList<>(merged.values());
        if ("unpaid".equals(intent)) {
            hits.sort(Comparator.comparing(SmartHit::totalOutstanding).reversed());
        }
        log.info("AI smart search: orgId={} intent={} hits={}", orgId, intent, hits.size());
        return new SmartSearchResponse(query, normalized, intent, hits);
    }

    private String detectIntent(String query) {
        String lower = query.toLowerCase();
        if (lower.contains("unpaid") || lower.contains("pending") || lower.contains("due")
            || lower.contains("outstanding") || lower.contains("owe")) {
            return "unpaid";
        }
        if (lower.contains("claim") || lower.contains("warranty") || lower.contains("amc")) {
            return "claim";
        }
        if (lower.contains("today") || lower.contains("recent") || lower.contains("latest")) {
            return "recent";
        }
        return "general";
    }

    private String relaxedQuery(String query) {
        // Drop short filler words so "innova unpaid last month" still matches "innova".
        StringBuilder out = new StringBuilder();
        for (String word : query.split("\\s+")) {
            if (word.length() >= 4) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(word);
            }
        }
        String relaxed = out.toString().trim();
        return relaxed.isEmpty() ? query : relaxed;
    }

    private void merge(Map<Long, SmartHit> merged, SearchResponse response, String whyRank) {
        for (SearchResponse.Hit hit : response.hits()) {
            merged.putIfAbsent(hit.customerId(), new SmartHit(hit.customerId(), hit.customerName(),
                hit.phone(), hit.totalOutstanding(), hit.matchField(), whyRank));
        }
    }
}
