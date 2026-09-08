package com.dams.recon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One statement line awaiting explanation (FEAT-40). match_kind is how the
 * suggestion was found: EXACT (UTR + amount), AMOUNT_DATE (amount + date±2d,
 * needs a human eye), MANUAL (accountant picked it). NULL = unmatched —
 * money in bank with no receipt, the actual work product.
 */
@Entity
@Table(name = "recon_line")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ReconLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private Long batchId;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(length = 60)
    private String utr;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String narration;

    @Column(name = "matched_settlement_line_id")
    private Long matchedSettlementLineId;

    @Column(name = "match_kind", length = 12)
    private String matchKind;

    @Column(nullable = false)
    private boolean ignored = false;

    public boolean isResolved() {
        return ignored || matchedSettlementLineId != null;
    }
}
