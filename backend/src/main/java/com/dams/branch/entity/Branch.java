package com.dams.branch.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * A dealership branch. First org-scoped entity — every row carries org_id, and the
 * "orgFilter" (defined on AppUser, enabled per request by TenantFilterActivator) keeps
 * one org's branches invisible to another. See AGENT.md multi-tenancy.
 *
 * `code` is 2–5 chars, unique per org (NOT globally) — two dealerships may both use "OOR".
 */
@Entity
@Table(name = "branch")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 5)
    private String code;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
