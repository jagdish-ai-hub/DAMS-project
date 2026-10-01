package com.dams.audit.entity;

/**
 * The kind of change an {@link AuditEvent} records. The full set is fixed in plan.md and
 * mirrored by a CHECK constraint on audit_event.event_type — Stage 3 only writes CREATED
 * and CATEGORY_CHANGED; later stages use the rest.
 */
public enum EventType {
    CREATED,
    SUBMITTED,
    VERIFIED,
    APPROVED,
    QUERIED,
    REJECTED,
    CLOSED,
    OVERRIDE,
    LINE_ADDED,
    SETTLED,
    CATEGORY_CHANGED,
    CLAIM_TYPE_CHANGED,
    TRANSFERRED_TO_CLAIM,
    /** Cashier sent an over-limit expense draft to the FM for pre-approval (rev 53). */
    APPROVAL_REQUESTED,
    /** FM pre-approved an over-limit expense draft (rev 53). */
    PRE_APPROVED,
    /** A user switched into another role (or back to their own) — rev 55. */
    ROLE_SWITCHED,
    /** A customer was attached to a customerless job card (or changed by Owner/FM) — rev 56. */
    JOB_CARD_CUSTOMER_ATTACHED
}
