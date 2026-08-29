package com.dams.attachment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * A PDF or image receipt attached to a document or a line. Stored in object storage
 * (Cloudflare R2 in prod, local filesystem in dev) — Postgres only holds the object key
 * plus metadata. Served via short-lived signed URLs, never a public link.
 *
 * {@code frozen} is set once the parent document is Approved or Closed — after that the
 * attachment cannot be replaced or deleted (AGENT.md "Attachments").
 */
@Entity
@Table(name = "attachment")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "parent_type", nullable = false, length = 20, updatable = false)
    private ParentType parentType;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private Long parentId;

    @Column(name = "r2_object_key", nullable = false, length = 255, updatable = false)
    private String objectKey;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private boolean frozen = false;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();
}
