package com.dams.spareparts.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/** A branch-to-branch request for a part: REQUESTED, then SENT (stock leaves), then RECEIVED. */
@Entity
@Table(name = "inv_transfer")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class InvTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    /** The branch that holds the part and would send it. */
    @Column(name = "from_branch_id", nullable = false, updatable = false)
    private Long fromBranchId;

    /** The branch that asked for the part. */
    @Column(name = "to_branch_id", nullable = false, updatable = false)
    private Long toBranchId;

    @Column(name = "part_id", nullable = false, updatable = false)
    private Long partId;

    @Column(name = "qty_requested", nullable = false, updatable = false)
    private int qtyRequested;

    @Column(name = "qty_sent")
    private Integer qtySent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TransferStatus status = TransferStatus.REQUESTED;

    @Column(name = "job_card_no", length = 40)
    private String jobCardNo;

    @Column(length = 500)
    private String note;

    /** The reason a request was declined or cancelled. */
    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "sent_by")
    private Long sentBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** The request number shown on screen and written to the ledger, e.g. "SP-12". */
    public String requestNo() {
        return "SP-" + id;
    }
}
