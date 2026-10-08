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
 * {@code claimTypeId} is the claim fact: null means this job card is not a claim; set,
 * it names which claim type (Warranty / AMC / CGW / ...). Selecting one on a receipt IS
 * the "Transfer to Claim" action — there is no separate button on the receive side (the
 * expense side keeps its own, since not every expense on a claim job is billable to it).
 *
 * {@code categoryId} / {@code businessStatusId} / {@code claimTypeId} are editable via
 * PATCH until a ClaimClose row exists for the job card (Stage 8); a category change is
 * audited (CATEGORY_CHANGED), a claim type change (CLAIM_TYPE_CHANGED). The screen
 * reference is {@code {branchCode}-JC-{id}}, built at read time.
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

    /** Nullable (rev 56) — a job card started from an Expense has no customer until a Receipt attaches one. */
    @Column(name = "customer_id")
    private Long customerId;

    /** Typed vehicle number kept as text while there is no customer to own a Vehicle row (rev 56). */
    @Column(name = "vehicle_no_text", length = 20)
    private String vehicleNoText;

    /** Nullable — counter sales have no vehicle. */
    @Column(name = "vehicle_id")
    private Long vehicleId;

    /** Contact number given at this receipt (rev 68). Optional, per job card — never copied onto the customer. */
    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    /** Vehicle chassis number (rev 68). Optional, uppercase, no spaces. */
    @Column(name = "chassis_no", length = 40)
    private String chassisNo;

    /** Chassis numbers are compared and shown uppercase with no spaces; blank means "not given". */
    public static String normaliseChassis(String raw) {
        if (raw == null) {
            return null;
        }
        String n = raw.replaceAll("\\s+", "").toUpperCase();
        return n.isEmpty() ? null : n;
    }

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

    /** Null when this job card is not a Warranty / AMC / CGW claim. */
    @Column(name = "claim_type_id")
    private Long claimTypeId;

    @Column(name = "business_status_id", nullable = false)
    private Long businessStatusId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
