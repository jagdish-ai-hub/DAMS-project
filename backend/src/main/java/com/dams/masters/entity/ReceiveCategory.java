package com.dams.masters.entity;

import com.dams.common.entity.OrgMaster;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Category of a Receive document — its Transaction Type (Workshop, Breakdown, Advance, …).
 *
 * {@code is_claim} is legacy: before the Claim Type redesign (see {@link ClaimType}) a job
 * card's claim status was read off this flag via its category. It is no longer read
 * anywhere — active rows are never claim rows now — and is kept only so the old,
 * deactivated Warranty / AMC / CGW category rows retain their original historical value.
 */
@Entity
@Table(name = "receive_category")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ReceiveCategory extends OrgMaster {

    @Column(name = "is_claim", nullable = false)
    private boolean claim = false;
}
