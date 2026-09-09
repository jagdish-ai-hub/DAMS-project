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

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Organization(String name) {
        this.name = name;
    }
}
