package com.dams.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps a user to specific branch IDs they can access.
 * Only used for ACCOUNTANT and CASHIER roles.
 * FM, OWNER, SUPER_ADMIN have org-wide access by convention — no rows needed.
 *
 * branch_id is a plain Long here; the Branch entity is created in V2/Stage2.
 * The FK constraint from branch_id to branch.id is added in V2 migration.
 */
@Entity
@Table(name = "user_branch_access")
@Getter
@Setter
@NoArgsConstructor
public class UserBranchAccess {

    @EmbeddedId
    private UserBranchAccessId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    // branch_id stored as plain column — FK to branch added in V2
    @Column(name = "branch_id", insertable = false, updatable = false)
    private Long branchId;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.EqualsAndHashCode
    public static class UserBranchAccessId implements java.io.Serializable {
        @Column(name = "user_id")
        private Long userId;

        @Column(name = "branch_id")
        private Long branchId;
    }
}
