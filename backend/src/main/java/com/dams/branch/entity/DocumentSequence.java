package com.dams.branch.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Gap-free counter, one row per (org, branch, month, doc type). Created in Stage 2,
 * first used for document numbering in Stage 4 — where {@code last_seq} is incremented
 * under {@code SELECT ... FOR UPDATE} inside the submit transaction. See plan.md.
 */
@Entity
@Table(name = "document_sequence")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class DocumentSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "month_key", nullable = false, length = 6, updatable = false)
    private String monthKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 4, updatable = false)
    private DocType docType;

    @Column(name = "last_seq", nullable = false)
    private int lastSeq = 0;
}
