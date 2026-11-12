package com.dams.spareparts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/** One confirmed Excel upload: the summary. Its stock effect is in the ledger rows that point at it. */
@Entity
@Table(name = "inv_import")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class InvImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ImportMode mode;

    @Column(name = "rows_total", nullable = false)
    private int rowsTotal;

    @Column(name = "rows_posted", nullable = false)
    private int rowsPosted;

    @Column(name = "rows_rejected", nullable = false)
    private int rowsRejected;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
