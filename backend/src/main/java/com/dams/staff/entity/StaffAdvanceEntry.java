package com.dams.staff.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only advance ledger entry (FEAT-44): ADVANCE out, RECOVERY in.
 * Outstanding per staff is derived (advances − recoveries), never stored —
 * a stored balance would drift from its own ledger. Rows are never edited or
 * deleted; a wrong entry is fixed by an opposing entry, like cash.
 */
@Entity
@Table(name = "staff_advance_entry")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class StaffAdvanceEntry {

    public static final String ADVANCE = "ADVANCE";
    public static final String RECOVERY = "RECOVERY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private Long staffId;

    @Column(nullable = false, length = 10)
    private String kind;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(length = 500)
    private String note;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
