package com.dams.masters.entity;

import com.dams.common.entity.OrgMaster;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/** How an expense line was paid (Cash, QR / UPI, Bank). */
@Entity
@Table(name = "expense_mode")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@NoArgsConstructor
public class ExpenseMode extends OrgMaster {
}
