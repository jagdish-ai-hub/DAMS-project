package com.dams.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * An extra role the Owner lets a user switch into (plan.md rev 55). FINANCE_MANAGER is org-wide
 * ({@code branchId} null); ACCOUNTANT / CASHIER name the one branch they may act at, so a user
 * granted Cashier at two branches has two rows. The user's own primary role lives on
 * {@link AppUser#getRole()} and never appears here.
 */
@Entity
@Table(name = "user_role_grant")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class UserRoleGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    /** Null only for FINANCE_MANAGER (org-wide). */
    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
