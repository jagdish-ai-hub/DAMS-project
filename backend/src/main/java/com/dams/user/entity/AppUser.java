package com.dams.user.entity;

import com.dams.organization.entity.Organization;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.Instant;

/**
 * Application user. Named AppUser (not User) to avoid collision with
 * Spring Security's UserDetails class and PostgreSQL's reserved "user" keyword.
 *
 * org_id is NULL for SUPER_ADMIN — the only cross-org role.
 * Every other role must have an org_id. See AGENT.md multi-tenancy section.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
// Global definition of the org_id tenant filter. Declared here (on a managed class) so it is
// registered once; individual tenant-scoped entities opt in with @Filter(name = "orgFilter",
// condition = "org_id = :orgId") starting in Stage 3. TenantFilterActivator enables it per request.
@FilterDef(name = "orgFilter", parameters = @ParamDef(name = "orgId", type = Long.class))
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * NULL for SUPER_ADMIN. All other roles must have an org.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private Organization organization;

    /**
     * The single branch a CASHIER posts every document under. Exactly one, mandatory for CASHIER,
     * null for every other role. Plain Long — the FK to branch(id) is added in the V2 migration.
     * The org's multi_branch_cashier_access toggle never changes this. See plan.md locked decisions.
     */
    @Column(name = "home_branch_id")
    private Long homeBranchId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "invite_token")
    private String inviteToken;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
