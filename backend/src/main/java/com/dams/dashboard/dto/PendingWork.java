package com.dams.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The Owner dashboard's "stuck with whom" card (rev 71): every entry still waiting on a person,
 * grouped by who has to act next — Cashier, Accountant, Finance Manager. Read-only.
 */
public record PendingWork(List<Group> groups) {

    /**
     * @param holder      CASHIER | ACCOUNTANT | FINANCE_MANAGER
     * @param count       entries waiting on them (the Cashier's unsent drafts are NOT in this)
     * @param amount      their value
     * @param draftCount  Cashier only — drafts never submitted, shown beside the count
     * @param draftAmount their value
     * @param items       count + drafts, oldest first
     */
    public record Group(String holder, String label, int count, BigDecimal amount,
                        int draftCount, BigDecimal draftAmount, List<Item> items) {
    }

    /**
     * One entry waiting on someone.
     *
     * @param stage plain-words reason it is with them, e.g. "Awaiting verification"
     * @param since when it was submitted (created, for a draft) — the days-waiting clock
     */
    public record Item(String type, Long id, String documentNo, Long branchId, String branchCode,
                       String party, String category, BigDecimal amount, String workflowStatus,
                       String stage, boolean draft, java.time.Instant since) {
    }
}
