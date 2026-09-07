package com.dams.budget.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A monthly spend cap for one expense category (Owner-set). One row per
 * {@code (org, category, month)} — {@code monthKey} is YYYYMM (e.g. 202608).
 *
 * Caps never block a submission — they only inform (dashboard / review compare spend
 * against the cap at read time). Rows are upserted, never hard-deleted by the app.
 */
@Entity
@Table(name = "expense_budget")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private Long categoryId;

    @Column(name = "month_key", nullable = false, updatable = false, length = 6)
    private String monthKey;

    @Column(name = "cap_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal capAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
