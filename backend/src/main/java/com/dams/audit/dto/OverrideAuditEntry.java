package com.dams.audit.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One amount override, for the org-wide Override Audit screen (AGENT.md decision #4). Covers
 * both kinds of override in the system:
 *
 *   - {@code kind = "receipt"} / {@code "expense"} — an Accountant's provisional line override;
 *     {@code documentNo} is the document, {@code lineId} the line.
 *   - {@code kind = "claim"} — the Finance Manager's final, locked claim-close override;
 *     {@code documentNo} is the job-card reference, {@code lineId} is null, {@code amountBefore}
 *     is what was actually received and {@code amountAfter} the figure it was closed at.
 */
public record OverrideAuditEntry(
    Instant at,
    String kind,
    String actorName,
    Long branchId,
    String branchCode,
    String documentNo,
    String lineId,
    BigDecimal amountBefore,
    BigDecimal amountAfter,
    String reason
) {
}
