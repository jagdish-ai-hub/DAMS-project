package com.dams.spareparts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/** One cell of the Owner's permission grid. A missing row means the default in SparePermission. */
@Entity
@Table(name = "inv_role_permission")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class InvRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** INVENTORY_MANAGER or STORE_KEEPER. */
    @Column(nullable = false, updatable = false, length = 30)
    private String role;

    /** A SparePermission name. */
    @Column(nullable = false, updatable = false, length = 30)
    private String permission;

    @Column(nullable = false)
    private boolean allowed;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
