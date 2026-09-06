package com.dams.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * One answered Owner/Admin assistant question (FEAT-09). Append-only traceability:
 * a user report of "the bot said X" resolves to the exact server log line via
 * {@code requestId} (AGENT.md: every error/log line carries a request ID).
 * Never updated or deleted by the app — only purged with the org.
 */
@Entity
@Table(name = "ai_query_log")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class AiQueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 500, updatable = false)
    private String question;

    @Column(name = "cited_docs", nullable = false, length = 1000, updatable = false)
    private String citedDocs = "";

    @Column(name = "request_id", length = 36, updatable = false)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
