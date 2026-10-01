package com.dams.myentries.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The two message boxes on the Cashier home page (rev 57).
 *
 * {@code queries} (left): documents the Accountant or Finance Manager sent back to the cashier.
 * {@code approvals} (right): expenses the cashier asked the Finance Manager to pre-approve, and the replies.
 *
 * The counts are the unattended items only -- {@code needsAction} rows. They are derived from each
 * document's CURRENT state, so resubmitting / acting on a document removes it and lowers the count
 * with no read-tracking.
 */
public record CashierInboxResponse(
    List<Item> queries,
    List<Item> approvals,
    int queriesToAct,
    int approvalsToAct
) {

    /**
     * @param kind     RECEIPT | EXPENSE | CASH (drives which screen opens)
     * @param state    QUERIED | REJECTED | PRE_PENDING | PRE_APPROVED | PRE_QUERIED
     * @param fromName who wrote the reply (null for a request still waiting)
     * @param fromRole their role label, e.g. ACCOUNTANT / FINANCE_MANAGER
     * @param note     the question / rejection reason / approval summary
     */
    public record Item(
        Long id,
        String kind,
        String documentNo,
        String title,
        BigDecimal total,
        String state,
        boolean needsAction,
        String fromName,
        String fromRole,
        String note,
        Instant at
    ) {
    }
}
