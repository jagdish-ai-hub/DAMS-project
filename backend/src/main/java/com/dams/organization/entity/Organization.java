package com.dams.organization.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "organization")
@Getter
@Setter
@NoArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /**
     * When true, cashiers in this org can search/see customers and job cards across all branches.
     * It never changes which branch a cashier's own documents post under. Default OFF —
     * see AGENT.md / plan.md locked decisions.
     */
    @Column(name = "multi_branch_cashier_access", nullable = false)
    private boolean multiBranchCashierAccess = false;

    /**
     * |Variance| above this closes PENDING countersign instead of locking
     * clean (FEAT-41). NULL = feature off (existing behaviour, zero friction).
     */
    @Column(name = "cash_variance_countersign_threshold", precision = 14, scale = 2)
    private java.math.BigDecimal cashVarianceCountersignThreshold;

    /**
     * Nightly owner digest via message (FEAT-42). Explicit opt-in — pushing
     * WhatsApp to an owner who never asked is spam, not a feature.
     */
    @Column(name = "digest_enabled", nullable = false)
    private boolean digestEnabled = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Organization(String name) {
        this.name = name;
    }
}
