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
    CATEGORY_CHANGED
}
