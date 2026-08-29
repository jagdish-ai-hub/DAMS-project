package com.dams.masters.entity;

import com.dams.common.entity.OrgMaster;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * A spend line under an expense category (Fuel (JC), Stationary, …). {@code limitAmount}
 * is the soft per-line limit — over it, the expense document is flagged {@code over_limit}
 * (it does not block submission). NULL = no limit.
 */
@Entity
@Table(name = "expense_sub_category")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseSubCategory extends OrgMaster {

    @Column(name = "expense_category_id", nullable = false)
    private Long expenseCategoryId;

    @Column(name = "limit_amount", precision = 14, scale = 2)
    private BigDecimal limitAmount;
}
