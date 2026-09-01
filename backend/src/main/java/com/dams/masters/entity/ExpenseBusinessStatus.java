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
 * Business status of an expense document (Open → In Progress → Awaiting Receipt →
 * Received Receipt → Closed / Transfer to Claim). A user-set label, separate from
 * workflow_status.
 *
 * {@code triggersClaim} marks the one status that moves an expense onto a warranty / AMC /
 * goodwill claim instead of being paid as cash. The service checks this flag, never the
 * label, and requires the expense to sit on a job card whose category is a claim category.
 */
@Entity
@Table(name = "expense_business_status")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseBusinessStatus extends OrgMaster {

    @Column(name = "triggers_claim", nullable = false)
    private boolean triggersClaim = false;
}
