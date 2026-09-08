package com.dams.jobcard.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Next step on an open warranty/AMC/CG claim (FEAT-38). Aging buckets show
 * OLD claims; this row records the NEXT STEP with an owner and a date, so
 * claims stop dying from neglect. Completing is explicit (done_at); rows are
 * never deleted — the chase history matters at write-off time.
 */
@Entity
@Table(name = "claim_action")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class ClaimAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "job_card_id", nullable = false, updatable = false)
    private Long jobCardId;

    @Column(nullable = false, length = 500)
    private String action;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "done_at")
    private Instant doneAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isOpen() {
        return doneAt == null;
    }
}
