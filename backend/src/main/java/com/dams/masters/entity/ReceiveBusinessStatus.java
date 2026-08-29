package com.dams.masters.entity;

import com.dams.common.entity.OrgMaster;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * Business status of a Receive/job-card case (Hold, WIP, Warranty, Close, …). A user-set
 * label — distinct from workflow_status and from the computed ReceiveDocument.settled flag.
 */
@Entity
@Table(name = "receive_business_status")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@NoArgsConstructor
public class ReceiveBusinessStatus extends OrgMaster {
}
