package com.dams.audit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * Append-only audit row. Written through {@link com.dams.audit.service.AuditService};
 * never updated or deleted (except by the Super Admin org-purge). Org-scoped.
 *
 * {@code detail} holds a small JSON object as text, e.g. {@code {"before":7,"after":9}}
 * for a CATEGORY_CHANGED event.
 */
@Entity
@Table(name = "audit_event")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 10)
    private ActorType actorType;

    /** Null when {@link #actorType} is SYSTEM. */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
