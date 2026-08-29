package com.dams.masters.entity;

import com.dams.common.entity.OrgMaster;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

/**
 * Top-level expense department (Service, Sales, Showroom, Finance). Sub-categories hang
 * off this via {@code expense_category_id}.
 */
@Entity
@Table(name = "expense_category")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@NoArgsConstructor
public class ExpenseCategory extends OrgMaster {
}
