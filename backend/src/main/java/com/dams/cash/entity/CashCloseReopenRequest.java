package com.dams.cash.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A cashier's request to reopen a locked cash day. The close itself is never edited in
 * place: on FM approval the {@link CashDayClose} row is deleted (so the day can be
 * re-closed with a fresh count) and this row flips to APPROVED; on rejection it flips to
 * REJECTED with the FM's reason kept in {@code decisionNote}. Decided rows are history —
 * only one PENDING row per (branch, date) may exist (partial unique index, see V24), so a
 * rejected request never blocks a fresh application.
 */
@Entity
@Table(name = "cash_close_reopen_request")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class CashCloseReopenRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "close_date", nullable = false, updatable = false)
    private LocalDate closeDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReopenRequestStatus status = ReopenRequestStatus.PENDING;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
