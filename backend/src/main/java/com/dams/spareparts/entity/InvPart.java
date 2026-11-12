package com.dams.spareparts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;

/** Part master, one row per part code per organization. The price is org-wide. */
@Entity
@Table(name = "inv_part")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class InvPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** Upper-case, no spaces: see SparePartsText.normalizePartCode. */
    @Column(name = "part_code", nullable = false, length = 40)
    private String partCode;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
