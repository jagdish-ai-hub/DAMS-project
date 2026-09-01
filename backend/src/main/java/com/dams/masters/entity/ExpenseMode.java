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
 * How an expense line was paid (Cash, QR / UPI, Bank). {@code requiresBank} /
 * {@code requiresRef} — the same two flags {@link SettlementMode} carries — drive which
 * fields the expense-line form makes mandatory. The form asks the mode; it never matches
 * the mode's name (AGENT.md "nothing hard-coded").
 */
@Entity
@Table(name = "expense_mode")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseMode extends OrgMaster {

    @Column(name = "requires_bank", nullable = false)
    private boolean requiresBank = false;

    @Column(name = "requires_ref", nullable = false)
    private boolean requiresRef = false;

    /** True for the physical-cash mode — these expense payments are Cash-page drawer withdrawals. */
    @Column(name = "is_cash", nullable = false)
    private boolean cash = false;
}
