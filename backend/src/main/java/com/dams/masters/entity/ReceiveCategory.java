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
 * Category of a Receive document (Workshop, AMC, Warranty, …). {@code is_claim} marks the
 * ones the Finance Manager closes with a ClaimClose (Warranty / AMC / CG). A job card's
 * {@code is_claim} is read from its category via this flag — never stored separately.
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
