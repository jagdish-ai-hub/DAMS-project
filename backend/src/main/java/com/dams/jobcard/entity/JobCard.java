package com.dams.jobcard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The case — it anchors a customer (and usually a vehicle) to a branch and carries the
 * case-level money fields. One invoice per job; over its life a job card can own several
 * ReceiveDocuments (Stage 4).
 *
 * {@code is_claim} is deliberately NOT stored here — it is read from
 * {@code receive_category.is_claim} via {@link #categoryId}, so the flag can never drift.
 *
 * {@code categoryId} / {@code businessStatusId} are editable via PATCH until a ClaimClose
 * row exists for the job card (Stage 8); a category change is audited (CATEGORY_CHANGED).
 * The screen reference is {@code {branchCode}-JC-{id}}, built at read time.
 */
@Entity
@Table(name = "job_card")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class JobCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** Nullable — counter sales have no vehicle. */
    @Column(name = "vehicle_id")
    private Long vehicleId;

    /** Eicher's external job-card number. Nullable, manual, never used as a key. */
    @Column(name = "dbm_id", length = 40)
    private String dbmId;

    /** External reference. Nullable, manual, never DAMS-generated. */
    @Column(name = "invoice_no", length = 60)
    private String invoiceNo;

    @Column(name = "invoice_amount", precision = 14, scale = 2)
    private BigDecimal invoiceAmount;

    /** B2C by default. When true, {@link #gstNo} is required (enforced in JobCardService). */
    @Column(name = "is_b2b", nullable = false)
    private boolean b2b = false;

    /** Customer's GST number — mandatory for a B2B job card, null otherwise. */
    @Column(name = "gst_no", length = 20)
    private String gstNo;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "business_status_id", nullable = false)
    private Long businessStatusId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
