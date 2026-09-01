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
 * The first-ever drawer opening for a branch, set once by the Accountant. Every subsequent
 * day's opening is the previous day's {@link CashDayClose#getCountedAmount()} — this row is
 * only the starting point of that chain. Unique per branch.
 */
@Entity
@Table(name = "branch_cash_opening")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class BranchCashOpening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "opening_date", nullable = false)
    private LocalDate openingDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "set_by", nullable = false, updatable = false)
    private Long setBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
