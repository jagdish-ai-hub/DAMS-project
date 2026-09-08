package com.dams.recon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * One bank-statement upload (FEAT-40). The batch is the unit of work: upload
 * a CSV, get match suggestions per line, confirm or ignore each. Matching
 * never moves money or edits a document — it only explains money.
 */
@Entity
@Table(name = "recon_batch")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ReconBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "line_count", nullable = false)
    private int lineCount = 0;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();
}
