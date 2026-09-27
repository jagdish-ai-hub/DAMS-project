package com.dams.expense.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * The review wrapper around one expense cause — money paid OUT to a receiver (vendor /
 * payee), optionally tagged to a job card. Mirrors {@code ReceiveDocument}, with three
 * differences: it is billed to a receiver not a customer, {@code jobCardId} is optional
 * (overhead expenses have none), and there is no pending-amount / auto-settle math — the
 * Accountant closes it explicitly (Stage 7), which is why {@link ExpenseWorkflowStatus}
 * has the extra CLOSED value.
 *
 * There is deliberately no "one open document per ..." invariant here (see V14). FK ids are
 * stored as plain Long (project convention), not {@code @ManyToOne}.
 */
@Entity
@Table(name = "expense_document")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** Always the posting cashier's home branch — never taken from the request. */
    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    /** Nullable — an expense may be pure branch overhead with no job card. */
    @Column(name = "job_card_id")
    private Long jobCardId;

    /**
     * Manual reference fields, never used as keys — set directly on the document (not
     * derived from job_card), so they're available even when there's no linked job card.
     * When the doc also has a job card, its own value wins in the read model and falls
     * back to the job card's for docs that only ever set it there.
     */
    @Column(name = "customer_name", length = 160)
    private String customerName;

    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo;

    @Column(name = "invoice_no", length = 60)
    private String invoiceNo;

    /** Eicher's external job-card number (see JobCard.dbmId). Nullable, manual. */
    @Column(name = "dbm_id", length = 40)
    private String dbmId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "expense_category_id", nullable = false)
    private Long expenseCategoryId;

    @Column(name = "business_status_id", nullable = false)
    private Long businessStatusId;

    /** e.g. {@code OOR-AUG26-E-001}. Null until the document is submitted. */
    @Column(name = "document_no", length = 40)
    private String documentNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 12)
    private ExpenseWorkflowStatus workflowStatus = ExpenseWorkflowStatus.DRAFT;

    /**
     * True when any line's amount exceeds its sub-category's limit. Recomputed by the
     * service on every line change; never trusted from the request.
     */
    @Column(name = "over_limit", nullable = false)
    private boolean overLimit = false;

    /** FM pre-approval of an over-limit expense (rev 53, V31). Null = never requested. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pre_approval_status", length = 12)
    private PreApprovalStatus preApprovalStatus;

    /** The document total the FM approved; a higher total needs approval again. */
    @Column(name = "pre_approved_amount", precision = 14, scale = 2)
    private java.math.BigDecimal preApprovedAmount;

    @Column(name = "pre_approved_by")
    private Long preApprovedBy;

    @Column(name = "pre_approved_at")
    private Instant preApprovedAt;

    @Column(name = "approval_requested_at")
    private Instant approvalRequestedAt;

    /**
     * Monotonic line-number counter (V22) — see ReceiveDocument.lineNoSeq.
     * Numbers are never reused even after a line is voided.
     */
    @Column(name = "line_no_seq", nullable = false)
    private int lineNoSeq = 0;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;
}
