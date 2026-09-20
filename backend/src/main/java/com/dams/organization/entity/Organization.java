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
     * When true, an Accountant may approve a SUBMITTED receipt directly — no claim type,
     * business status not "Credit", every settlement line cash-mode — skipping the Finance
     * Manager. Default OFF: a carve-out from "FM gives final approval on every entry"
     * (AGENT.md) that an org must opt into. See ReviewService#directApproveReceipt.
     */
    @Column(name = "accountant_direct_approve_cash", nullable = false)
    private boolean accountantDirectApproveCash = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Organization(String name) {
        this.name = name;
    }
}
