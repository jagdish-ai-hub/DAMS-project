package com.dams.estimate.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * One quoted line on an estimate (FEAT-48): "Clutch plate + labour — 8,500".
 * Line numbers are append-only within the estimate (same never-reuse rule as
 * document lines) so a variance discussion can point at "line 2".
 */
@Entity
@Table(name = "estimate_line")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class EstimateLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "estimate_id", nullable = false, updatable = false)
    private Long estimateId;

    @Column(name = "line_no", nullable = false, updatable = false)
    private int lineNo;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
