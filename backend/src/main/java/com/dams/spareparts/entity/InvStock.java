package com.dams.spareparts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * A branch's quantity of one part. This is the running total of the ledger (inv_movement) for the
 * same branch + part; only StockLedgerService changes it, and always together with a ledger row.
 */
@Entity
@Table(name = "inv_stock")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class InvStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "part_id", nullable = false, updatable = false)
    private Long partId;

    @Column(length = 60)
    private String bin;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
