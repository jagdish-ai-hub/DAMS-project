package com.dams.masters.entity;

import com.dams.user.entity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * Grants one role permission to set one job-card business status.
 *
 * Absence of a row is the denial — there is no "denied" flag. A status with no rows at all
 * can be set by nobody, which is why {@link com.dams.masters.service.ReceiveStatusAccessService}
 * refuses to save an empty role set rather than letting a status quietly become unusable.
 */
@Entity
@Table(name = "receive_business_status_role")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ReceiveBusinessStatusRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "status_id", nullable = false)
    private Long statusId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
