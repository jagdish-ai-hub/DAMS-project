package com.dams.messaging.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * Append-only send log (FEAT-36). Every send attempt leaves a row — reminders
 * are auditable, never fire-and-forget. LOGGED = recorded via the default
 * logging sender (no provider configured yet); SENT = provider accepted it.
 */
@Entity
@Table(name = "message_log")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class MessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 10)
    private String channel;

    @Column(name = "to_phone", nullable = false, length = 20)
    private String toPhone;

    @Column(name = "template_code", length = 60)
    private String templateCode;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false, length = 10)
    private String status = "QUEUED";

    @Column(name = "related_type", length = 30)
    private String relatedType;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(length = 500)
    private String error;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
