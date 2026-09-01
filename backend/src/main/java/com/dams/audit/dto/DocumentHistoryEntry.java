package com.dams.audit.dto;

import java.time.Instant;

/**
 * One line of a document's history, as shown in the review detail pane and (for a queried
 * item) to the cashier fixing it. A humanised projection of an {@link com.dams.audit.entity.AuditEvent}
 * — never stored, always derived.
 *
 * {@code note} carries the free text that belongs with the event (a query question, a
 * rejection reason, an override's "₹800 → ₹1,000 — wrong rate"), or null when the event
 * has none.
 */
public record DocumentHistoryEntry(
    String actor,        // user's name, or "System" for machine events
    String action,       // "Verified", "Queried", "Overrode a line amount", …
    String note,         // the query / reject / override text, or null
    Instant at
) {
}
