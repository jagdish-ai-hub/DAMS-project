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
 * How a settlement line was paid (Cash, QR / UPI, Bank, …). {@code requiresBank} /
 * {@code requiresRef} drive which fields the settlement-line form makes mandatory.
 */
@Entity
@Table(name = "settlement_mode")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class SettlementMode extends OrgMaster {

    @Column(name = "requires_bank", nullable = false)
    private boolean requiresBank = false;

    @Column(name = "requires_ref", nullable = false)
    private boolean requiresRef = false;

    /** True for physical-cash modes (Cash, Adv-Cash) — these feed the Cash-page drawer. */
    @Column(name = "is_cash", nullable = false)
    private boolean cash = false;
}
