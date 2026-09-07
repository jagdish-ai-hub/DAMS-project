package com.dams.ai.service;

import com.dams.ai.dto.AiMastersDtos.LimitAdvice;
import com.dams.ai.dto.AiMastersDtos.MastersHealthItem;
import com.dams.ai.dto.AiMastersDtos.ReceiverDuplicate;
import com.dams.config.TenantContext;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.masters.MasterType;
import com.dams.masters.dto.MasterResponse;
import com.dams.masters.service.MastersService;
import com.dams.receiver.dto.ReceiverResponse;
import com.dams.receiver.service.ReceiverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Masters hygiene (FEAT-17 receiver duplicates, FEAT-18 masters health, FEAT-19
 * limit advice). Suggestions only — every write still goes through the existing
 * Owner {@code PATCH /masters} / receiver endpoints (deactivate, never delete).
 */
@Service
public class AiMastersService {

    private static final Logger log = LoggerFactory.getLogger(AiMastersService.class);

    private final MastersService mastersService;
    private final ReceiverService receiverService;
    private final ExpenseDocumentRepository expenseDocumentRepo;

    public AiMastersService(MastersService mastersService,
                            ReceiverService receiverService,
                            ExpenseDocumentRepository expenseDocumentRepo) {
        this.mastersService = mastersService;
        this.receiverService = receiverService;
        this.expenseDocumentRepo = expenseDocumentRepo;
    }

    /** Likely-duplicate vendors sharing a normalised name or phone (FEAT-17). */
    @Transactional(readOnly = true)
    public List<ReceiverDuplicate> receiverDuplicates() {
        Long orgId = TenantContext.requireOrgId();
        List<ReceiverResponse> receivers = receiverService.list();
        List<ReceiverDuplicate> pairs = new ArrayList<>();
        for (int left = 0; left < receivers.size(); left++) {
            for (int right = left + 1; right < receivers.size(); right++) {
                ReceiverResponse first = receivers.get(left);
                ReceiverResponse second = receivers.get(right);
                String reason = duplicateReason(first, second);
                if (reason != null) {
                    pairs.add(new ReceiverDuplicate(first.id(), first.name(),
                        second.id(), second.name(), reason));
                }
            }
        }
        log.info("AI receiver duplicates built: orgId={} receivers={} pairs={}",
            orgId, receivers.size(), pairs.size());
        return pairs;
    }

    /** Dead or confusing dropdown rows across all eight master lists (FEAT-18). */
    @Transactional(readOnly = true)
    public List<MastersHealthItem> mastersHealth() {
        Long orgId = TenantContext.requireOrgId();
        List<MastersHealthItem> items = new ArrayList<>();
        for (MasterType type : MasterType.values()) {
            List<MasterResponse> rows = type.isExpenseSubCategory()
                ? allSubCategories()
                : mastersService.list(type, null);
            items.addAll(duplicateNames(type.slug(), rows));
            if (rows.isEmpty()) {
                items.add(new MastersHealthItem(type.slug(), "—", "empty-list",
                    "This list is empty — add rows or hide the dropdown."));
            }
        }
        log.info("AI masters health built: orgId={} issues={}", orgId, items.size());
        return items;
    }

    /** Data-backed limit revision hints per expense sub-category (FEAT-19). */
    @Transactional(readOnly = true)
    public List<LimitAdvice> limitAdvice() {
        Long orgId = TenantContext.requireOrgId();
        long overLimitDocs = 0;
        for (ExpenseDocument doc : expenseDocumentRepo
            .findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(orgId, ExpenseWorkflowStatus.SUBMITTED)) {
            if (doc.isOverLimit()) {
                overLimitDocs++;
            }
        }
        List<LimitAdvice> advice = new ArrayList<>();
        for (MasterResponse sub : allSubCategories()) {
            if (sub.limitAmount() == null) {
                continue;
            }
            // Only one name per concept (AGENT.md): the breach count is org-wide and
            // shared, the suggestion is per sub-category limit.
            advice.add(new LimitAdvice(sub.name(), sub.limitAmount(), overLimitDocs,
                overLimitDocs > 0
                    ? "There are " + overLimitDocs + " over-limit expenses awaiting review — "
                        + "check whether this limit still fits reality before changing it."
                    : "No over-limit expenses awaiting review — this limit looks right."));
        }
        log.info("AI limit advice built: orgId={} subCategories={}", orgId, advice.size());
        return advice;
    }

    // --- internals ---

    private String duplicateReason(ReceiverResponse first, ReceiverResponse second) {
        // A name alone is never a safe key (AGENT.md) — flag, don't merge.
        if (!first.active() || !second.active()) {
            return null;
        }
        if (samePhone(first.phone(), second.phone())) {
            return "Same phone number on two active vendors";
        }
        if (normalise(first.name()).equals(normalise(second.name()))
            && !normalise(first.name()).isEmpty()) {
            return "Same vendor name with different spelling or spacing";
        }
        return null;
    }

    private boolean samePhone(String left, String right) {
        String digitsLeft = digits(left);
        String digitsRight = digits(right);
        return digitsLeft.length() >= 10 && digitsLeft.equals(digitsRight);
    }

    private String digits(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (Character.isDigit(ch)) {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private String normalise(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char ch : value.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private List<MasterResponse> allSubCategories() {
        List<MasterResponse> all = new ArrayList<>();
        for (MasterResponse category : mastersService.list(MasterType.EXPENSE_CATEGORIES, null)) {
            all.addAll(mastersService.list(MasterType.EXPENSE_SUB_CATEGORIES, category.id()));
        }
        return all;
    }

    private List<MastersHealthItem> duplicateNames(String slug, List<MasterResponse> rows) {
        List<MastersHealthItem> items = new ArrayList<>();
        Map<String, List<MasterResponse>> byName = new HashMap<>();
        for (MasterResponse row : rows) {
            byName.computeIfAbsent(normalise(row.name()), key -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<String, List<MasterResponse>> entry : byName.entrySet()) {
            if (entry.getValue().size() <= 1 || entry.getKey().isEmpty()) {
                continue;
            }
            // M9: two dead rows need no action — only flag pairs with something live.
            boolean anyActive = false;
            List<String> names = new ArrayList<>();
            for (MasterResponse row : entry.getValue()) {
                names.add(row.name());
                if (row.active()) {
                    anyActive = true;
                }
            }
            if (anyActive) {
                items.add(new MastersHealthItem(slug, entry.getValue().get(0).name(), "duplicate-name",
                    "Two rows read as the same name (" + String.join(" / ", names)
                        + ") — deactivate one so entries stop splitting."));
            }
        }
        return items;
    }
}
