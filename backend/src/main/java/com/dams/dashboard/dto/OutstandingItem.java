package com.dams.dashboard.dto;

import java.math.BigDecimal;

/**
 * One line on the "money still owed / awaiting" list. {@code kind} is one of
 * {@code "job-card"} (a part-paid job), {@code "b2b"} (a B2B credit invoice), or
 * {@code "claim"} (an approved warranty / AMC / CG claim not yet settled by Eicher).
 */
public record OutstandingItem(
    String kind,
    String name,
    String sub,          // vehicle · branch · reference, one line
    BigDecimal amount,   // amount still outstanding
    String documentNo,
    String branchCode
) {
}
