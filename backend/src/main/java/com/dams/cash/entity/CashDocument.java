package com.dams.cash.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One internal bank &lt;-&gt; drawer cash movement (AGENT.md decision #1). A single amount,
 * <b>no sub-lines</b>, and no customer / vehicle / job-card — this is internal money
 * movement, not a customer document. It carries its own {@code {branch}-{MONKEY}-C-{seq}}
 * number and the same maker-checker workflow as every other document.
 *
 * FK ids are stored as plain Long (project convention), not {@code @ManyToOne}.
 */
@Entity
@Table(name = "cash_document")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class CashDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** Always the posting cashier's home branch — never taken from the request. */
    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    /** e.g. {@code OOR-AUG26-C-001}. Null until the document is submitted. */
    @Column(name = "document_no", length = 40)
    private String documentNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CashDirection direction;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "bank_id")
    private Long bankId;

    @Column(name = "transaction_ref", length = 80)
    private String transactionRef;

    @Column(length = 300)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 12)
    private CashWorkflowStatus workflowStatus = CashWorkflowStatus.DRAFT;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "submitted_at")
    private Instant submittedAt;
}
