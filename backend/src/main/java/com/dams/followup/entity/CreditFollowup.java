package com.dams.followup.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Collection follow-up for one receive document posted on credit (FEAT-35).
 * The dashboard outstanding list shows what is owed; this row owns the next
 * step: a due date plus the customer's promise. Overdue is derived at read
 * time (OPEN/PROMISED + due_date &lt; today) so it can never go stale.
 * One live row per document — re-promising reuses the row, history stays
 * in the audit trail and the reminder log.
 */
@Entity
@Table(name = "credit_followup")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class CreditFollowup {

    public static final String OPEN = "OPEN";
    public static final String PROMISED = "PROMISED";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "receive_document_id", nullable = false, updatable = false)
    private Long receiveDocumentId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "promise_note", length = 500)
    private String promiseNote;

    @Column(nullable = false, length = 10)
    private String status = OPEN;

    @Column(name = "reminded_count", nullable = false)
    private int remindedCount = 0;

    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isLive() {
        return OPEN.equals(status) || PROMISED.equals(status);
    }
}
