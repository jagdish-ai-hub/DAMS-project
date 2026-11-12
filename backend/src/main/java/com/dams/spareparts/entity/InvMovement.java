package com.dams.spareparts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/** One row of the stock ledger. Append-only: never updated, never deleted. */
@Entity
@Table(name = "inv_movement")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class InvMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "part_id", nullable = false, updatable = false)
    private Long partId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 20)
    private MovementType movementType;

    @Column(name = "qty_change", nullable = false, updatable = false)
    private int qtyChange;

    /** The branch quantity right after this row. */
    @Column(name = "qty_after", nullable = false, updatable = false)
    private int qtyAfter;

    /** Job card number, or the request number ("SP-12"). */
    @Column(updatable = false, length = 60)
    private String reference;

    @Column(name = "vehicle_no", updatable = false, length = 20)
    private String vehicleNo;

    @Column(updatable = false, length = 500)
    private String note;

    @Column(name = "import_id", updatable = false)
    private Long importId;

    @Column(name = "transfer_id", updatable = false)
    private Long transferId;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    /** Set only while the user was acting in a switched role. */
    @Column(name = "acting_role", updatable = false, length = 50)
    private String actingRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
