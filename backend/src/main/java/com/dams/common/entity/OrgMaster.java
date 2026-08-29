package com.dams.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Shared shape of the Owner-editable lookup masters: an org-scoped row with a name,
 * an active flag (rows are deactivated, never deleted), and a sort order for dropdowns.
 *
 * Concrete master entities add their own columns and the tenant {@code @Filter}
 * (see AGENT.md multi-tenancy). {@code org_id} is a plain column here — the service
 * layer sets it from TenantContext and reads via findByIdAndOrgId(...).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class OrgMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
