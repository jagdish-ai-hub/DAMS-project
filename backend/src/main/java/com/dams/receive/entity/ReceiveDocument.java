package com.dams.receive.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * The review / money-flow wrapper around one job card — NOT the case itself. A job card can
 * own several of these over its life (a claim closes, months later new money arrives → a new
 * document, same job card), but only ONE may be open (settled = false) at a time — enforced
 * by a partial unique index (see V11). "Add Payment" appends a line to that open document.
 *
 * Case-level fields (customer, vehicle, invoice, category, business status) live on JobCard,
 * not here. FK ids are stored as plain Long (project convention), not @ManyToOne.
 */
@Entity
@Table(name = "receive_document")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ReceiveDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** Always the job card's branch — never taken from the request. See ReceiveDocumentService. */
    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "job_card_id", nullable = false, updatable = false)
    private Long jobCardId;

    /** e.g. {@code OOR-AUG26-R-001}. Null until the document is submitted. */
    @Column(name = "document_no", length = 40)
    private String documentNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 12)
    private WorkflowStatus workflowStatus = WorkflowStatus.DRAFT;

    /**
     * Computed: flips true when the job card's Pending Amount reaches 0 (or, from Stage 8,
     * when a ClaimClose is written). Never set directly by a user action.
     */
    @Column(nullable = false)
    private boolean settled = false;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;
}
