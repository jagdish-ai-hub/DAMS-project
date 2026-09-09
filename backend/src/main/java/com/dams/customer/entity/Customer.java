package com.dams.customer.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * A person or company the dealership deals with. Org-scoped (the "orgFilter" keeps one
 * org's customers invisible to another). Not branch-scoped — a JobCard is what ties a
 * customer to a branch. Names are not unique; two real customers can share a name.
 */
@Entity
@Table(name = "customer")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 160)
    private String name;

    /** Nullable — advances and walk-ins often have no phone on file. */
    @Column(length = 32)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
