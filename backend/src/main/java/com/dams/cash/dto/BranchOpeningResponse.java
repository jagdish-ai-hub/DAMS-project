package com.dams.cash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One branch's one-time opening cash balance (rev 97) for the Accountant's "Branch opening" screen.
 * {@code set} is false until the Accountant records it; {@code locked} is true once it is set or the
 * branch has closed a day, after which the opening can no longer be recorded.
 */
public record BranchOpeningResponse(
    Long branchId,
    String branchCode,
    String branchName,
    boolean set,
    BigDecimal amount,
    LocalDate openingDate,
    boolean locked
) {
}
