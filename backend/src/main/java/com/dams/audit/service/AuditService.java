package com.dams.audit.service;

import com.dams.audit.entity.ActorType;
import com.dams.audit.entity.AuditEvent;
import com.dams.audit.entity.EventType;
import com.dams.audit.repository.AuditEventRepository;
import com.dams.config.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * The one place audit rows are written. Append-only — callers record a fact, they never
 * update or read back through here. {@code detail} is serialised to a compact JSON string.
 *
 * Stage 3 users: JobCard CREATED and CATEGORY_CHANGED. Later stages record the workflow
 * transitions (SUBMITTED / VERIFIED / APPROVED / OVERRIDE / SETTLED / …) the same way.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repo;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    /** Record a change made by the signed-in user. */
    @Transactional
    public void recordUserEvent(String entityType, Long entityId, EventType eventType,
                                Long actorUserId, Map<String, ?> detail) {
        write(entityType, entityId, null, eventType, ActorType.USER, actorUserId, detail);
    }

    /**
     * Record a user change, tagging the branch its document belongs to — used by the flows
     * that know the branch (review, claim close), so Override Audit / the dashboard can
     * filter by branch on an index. Passing {@code null} for {@code branchId} is the same as
     * {@link #recordUserEvent(String, Long, EventType, Long, Map)}.
     */
    @Transactional
    public void recordUserEvent(String entityType, Long entityId, Long branchId, EventType eventType,
                                Long actorUserId, Map<String, ?> detail) {
        write(entityType, entityId, branchId, eventType, ActorType.USER, actorUserId, detail);
    }

    /** Record a machine-made change (auto-settle, etc.) — actor id is null by definition. */
    @Transactional
    public void recordSystemEvent(String entityType, Long entityId, EventType eventType,
                                  Map<String, ?> detail) {
        write(entityType, entityId, null, eventType, ActorType.SYSTEM, null, detail);
    }

    /** Branch-tagged machine-made change — same as above with the document's branch recorded. */
    @Transactional
    public void recordSystemEvent(String entityType, Long entityId, Long branchId, EventType eventType,
                                  Map<String, ?> detail) {
        write(entityType, entityId, branchId, eventType, ActorType.SYSTEM, null, detail);
    }

    private void write(String entityType, Long entityId, Long branchId, EventType eventType,
                       ActorType actorType, Long actorId, Map<String, ?> detail) {
        AuditEvent event = new AuditEvent();
        event.setOrgId(TenantContext.requireOrgId());
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setBranchId(branchId);
        event.setEventType(eventType);
        event.setActorType(actorType);
        event.setActorId(actorId);
        event.setDetail(toJson(detail));
        repo.save(event);

        log.info("Audit: org={} {} #{} {} by {}{}", event.getOrgId(), entityType, entityId,
            eventType, actorType, actorId != null ? " #" + actorId : "");
    }

    private String toJson(Map<String, ?> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            // An audit row must still be written even if the detail can't be serialised.
            log.warn("Could not serialise audit detail {}: {}", detail, e.getMessage());
            return null;
        }
    }
}
