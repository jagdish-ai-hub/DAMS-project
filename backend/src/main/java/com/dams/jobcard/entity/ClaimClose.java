package com.dams.jobcard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The Finance Manager's final, immutable close of a claim — keyed by {@code job_card_id}
 * (you close the case, not one document in its history). Exactly one per job card.
 *
 * Stage 4 creates the table and reads it: once a row exists, the job card's Pending Amount
 * reports 0 (settled at {@code finalAmount}), PATCH /job-cards rejects category /
 * business-status changes, and POST /receipts against that job card is refused. The write
 * path (POST /job-cards/{id}/close-claim, which also flips settled on every open document)
 * arrives in Stage 8.
 */
@Entity
@Table(name = "claim_close")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ClaimClose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "job_card_id", nullable = false, updatable = false)
    private Long jobCardId;

    @Column(name = "final_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal finalAmount;

    @Column(nullable = false)
    private boolean overridden = false;

    @Column(name = "override_reason", length = 300)
    private String overrideReason;

    @Column(name = "closed_by", nullable = false, updatable = false)
    private Long closedBy;

    @Column(name = "closed_at", nullable = false, updatable = false)
    private Instant closedAt = Instant.now();
}
