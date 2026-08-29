package com.dams.masters.entity;

import com.dams.common.entity.OrgMaster;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * Business status of an expense document (Open → In Progress → Awaiting Receipt →
 * Received Receipt → Closed / Transfer to Claim). A user-set label, separate from
 * workflow_status.
 */
@Entity
@Table(name = "expense_business_status")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@NoArgsConstructor
public class ExpenseBusinessStatus extends OrgMaster {
}
