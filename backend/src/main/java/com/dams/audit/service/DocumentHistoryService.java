package com.dams.audit.service;

import com.dams.audit.dto.DocumentHistoryEntry;
import com.dams.audit.entity.ActorType;
import com.dams.audit.entity.AuditEvent;
import com.dams.audit.entity.EventType;
import com.dams.audit.repository.AuditEventRepository;
import com.dams.config.TenantContext;
import com.dams.user.entity.AppUser;
import com.dams.user.repository.AppUserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a document's raw {@link AuditEvent} rows into the chronological, human-readable
 * history the review pane shows (and that the cashier sees on a queried item). Read-only —
 * audit rows are written only through {@link AuditService}.
 */
@Service
public class DocumentHistoryService {

    private static final Logger log = LoggerFactory.getLogger(DocumentHistoryService.class);

    private final AuditEventRepository auditEventRepo;
    private final AppUserRepository userRepo;
    private final ObjectMapper objectMapper;

    public DocumentHistoryService(AuditEventRepository auditEventRepo,
                                  AppUserRepository userRepo,
                                  ObjectMapper objectMapper) {
        this.auditEventRepo = auditEventRepo;
        this.userRepo = userRepo;
        this.objectMapper = objectMapper;
    }

    /** Oldest-first history for one document ({@code entityType} e.g. "ReceiveDocument"). */
    @Transactional(readOnly = true)
    public List<DocumentHistoryEntry> forDocument(String entityType, Long documentId) {
        Long orgId = TenantContext.requireOrgId();
        List<AuditEvent> events = auditEventRepo
            .findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(orgId, entityType, documentId);

        Map<Long, String> nameCache = new HashMap<>();
        List<DocumentHistoryEntry> out = new ArrayList<>(events.size());
        // Repo returns newest-first; the pane reads top-to-bottom oldest-first.
        for (int i = events.size() - 1; i >= 0; i--) {
            AuditEvent e = events.get(i);
            out.add(new DocumentHistoryEntry(
                actorName(e, nameCache),
                action(e),
                note(e),
                e.getCreatedAt(),
                e.getActorRole()));
        }
        return out;
    }

    private String actorName(AuditEvent e, Map<Long, String> cache) {
        if (e.getActorType() == ActorType.SYSTEM || e.getActorId() == null) {
            return "System";
        }
        return cache.computeIfAbsent(e.getActorId(), id ->
            userRepo.findById(id).map(AppUser::getName).orElse("User #" + id));
    }

    private String action(AuditEvent e) {
        Map<String, Object> d = detail(e);
        return switch (e.getEventType()) {
            case CREATED -> "Created";
            case SUBMITTED -> {
                if (d.get("reopenedFrom") != null) {
                    yield "Reopened for review (new payment added after " + d.get("reopenedFrom") + ")";
                }
                yield Boolean.TRUE.equals(d.get("resubmit")) ? "Resubmitted" : "Submitted";
            }
            case LINE_ADDED -> "Line added";
            case VERIFIED -> "Verified";
            case APPROVED -> "Approved";
            case QUERIED -> Boolean.TRUE.equals(d.get("preApproval")) ? "Approval request queried" : "Queried";
            case REJECTED -> "Rejected";
            case OVERRIDE -> "Overrode a line amount";
            case CLOSED -> Boolean.TRUE.equals(d.get("claim"))
                ? "Claim closed" + (d.get("finalAmount") != null ? " — final ₹" + d.get("finalAmount") : "")
                    + (Boolean.TRUE.equals(d.get("overridden")) ? " (Overridden · Final)" : "")
                : "Closed";
            case SETTLED -> Boolean.TRUE.equals(d.get("unsettled"))
                ? "Reopened after being paid in full (new payment added)"
                : "Auto-settled — paid in full";
            case CATEGORY_CHANGED -> "Category changed";
            case CLAIM_TYPE_CHANGED -> "Claim type changed";
            case TRANSFERRED_TO_CLAIM -> "Transferred to claim";
            case APPROVAL_REQUESTED -> "Sent to Finance Manager for approval"
                + (d.get("amount") != null ? " (₹" + d.get("amount") + ")" : "");
            case PRE_APPROVED -> "Approved by Finance Manager"
                + (d.get("amount") != null ? " for ₹" + d.get("amount") : "") + " — ready to submit";
            // User-level event, never on a document — listed only so the switch stays exhaustive
            case ROLE_SWITCHED -> "Switched role";
            case JOB_CARD_CUSTOMER_ATTACHED -> "Customer linked to job card";
            case STATUS_CHANGED -> "Status changed"
                + (d.get("from") != null && d.get("to") != null ? ": " + d.get("from") + " → " + d.get("to") : "");
            case CASH_REOPENED -> "Cash day reopened"
                + (d.get("closeDate") != null ? " (" + d.get("closeDate") + ")" : "");
        };
    }

    /** The free-text that belongs with the event, if any — a query question, a reason, an override summary. */
    private String note(AuditEvent e) {
        Map<String, Object> d = detail(e);
        for (String key : List.of("note", "reason", "summary")) {
            Object v = d.get(key);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    private Map<String, Object> detail(AuditEvent e) {
        if (e.getDetail() == null || e.getDetail().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(e.getDetail(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            log.warn("Could not parse audit detail for event #{}: {}", e.getId(), ex.getMessage());
            return Map.of();
        }
    }
}
