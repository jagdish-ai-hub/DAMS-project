package com.dams.dashboard.dto;

import java.math.BigDecimal;

/**
 * Claims at a glance (rev 62): what was claimed from the manufacturer, what has come back, what
 * was rejected (a closed claim recovered less than claimed), and what is still open. Counts both
 * kinds of claim — expenses marked Transfer to Claim and warranty / AMC / CG receipts — with the
 * two kinds broken out. Claimed = received + rejected + pending.
 */
public record ClaimsSummary(
    String period,
    ClaimTotals total,
    ClaimTotals expenses,
    ClaimTotals receipts
) {

    /**
     * @param count    claims raised in the period
     * @param open     of those, claims not yet closed by the Finance Manager
     * @param claimed  expense: what was spent; receipt: the job card's invoice amount
     * @param received closed: the Finance Manager's final amount; open receipt claim: payments so far
     * @param rejected closed claims only — claimed minus the final amount, never below 0
     * @param pending  open claims only — claimed minus received so far, never below 0
     */
    public record ClaimTotals(int count, int open, BigDecimal claimed, BigDecimal received,
                              BigDecimal rejected, BigDecimal pending) {

        public static ClaimTotals empty() {
            return new ClaimTotals(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public ClaimTotals plus(ClaimTotals o) {
            return new ClaimTotals(count + o.count, open + o.open, claimed.add(o.claimed),
                received.add(o.received), rejected.add(o.rejected), pending.add(o.pending));
        }
    }
}
