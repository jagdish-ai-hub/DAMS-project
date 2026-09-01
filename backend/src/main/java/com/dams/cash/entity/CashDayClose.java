package com.dams.cash.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * End-of-day cash close for a {@code (branch, date)}. Its existence is the <b>lock</b>: once
 * a row exists, no cash movement — and no cash-mode settlement or expense line (enforced in
 * {@code CashDateLock}) — may be dated on or before {@code closeDate} for that branch.
 *
 * {@code computedClosing} is a snapshot of the drawer formula at close time;
 * {@code variance = countedAmount - computedClosing}; {@code varianceRemark} is required
 * (service layer) when the variance is non-zero. The next day's opening is this row's
 * {@code countedAmount}.
 */
@Entity
@Table(name = "cash_day_close")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class CashDayClose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "close_date", nullable = false, updatable = false)
    private LocalDate closeDate;

    @Column(name = "opening_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingAmount;

    @Column(name = "computed_closing", nullable = false, precision = 14, scale = 2)
    private BigDecimal computedClosing;

    @Column(name = "counted_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal countedAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal variance;

    @Column(name = "variance_remark", length = 300)
    private String varianceRemark;

    @Column(name = "closed_by", nullable = false, updatable = false)
    private Long closedBy;

    @Column(name = "closed_at", nullable = false, updatable = false)
    private Instant closedAt = Instant.now();
}
