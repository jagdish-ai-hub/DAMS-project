package com.dams.messaging.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * Org-owned message template (FEAT-36). Bodies use {{variable}} placeholders
 * rendered at send time — no free-typed blasts, so every outbound message is
 * reviewable copy. Codes are stable (payment_received, due_reminder,
 * renewal_reminder, owner_digest); channels are WHATSAPP with SMS fallback.
 */
@Entity
@Table(name = "message_template")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class MessageTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 10)
    private String channel = "WHATSAPP";

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
