package com.dams.masters.entity;

import com.dams.common.entity.OrgMaster;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * The claim types a job card can carry (Warranty, AMC, CGW, ...). Owner-editable, same
 * as every other master list — no extra columns beyond {@link OrgMaster}.
 */
@Entity
@Table(name = "claim_type")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ClaimType extends OrgMaster {
}
