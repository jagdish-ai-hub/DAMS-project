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
 * Business status of a Receive/job-card case (Received, Waiting for Claim, Credit, …). A
 * user-set label — distinct from workflow_status and from the computed
 * ReceiveDocument.settled flag.
 *
 * Which roles may set a given status is data, not code: see ReceiveBusinessStatusRole.
 *
 * {@code deprecated} is not the same as inactive. An inactive status leaves the dropdown
 * entirely; a deprecated one still works and still appears, just below the live statuses
 * and visibly marked — so the statuses in use before V26 keep working for staff who know
 * them, without cluttering the top of the list.
 */
@Entity
@Table(name = "receive_business_status")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ReceiveBusinessStatus extends OrgMaster {

    @Column(nullable = false)
    private boolean deprecated = false;
}
