package com.dams.cash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The live drawer position for one branch on one date, its component breakdown, whether the
 * day is already closed, and that day's movements — everything the Cash page needs in one call.
 */
public record CashDrawerResponse(
    Long branchId,
    String branchCode,
    String branchName,
    LocalDate date,

    BigDecimal opening,
    boolean openingSet,             // false → no close history and no configured opening yet
    BigDecimal cashReceipts,        // + cash-mode settlement received today
    BigDecimal cashIn,              // + cash_document IN today
    BigDecimal cashExpenses,        // − cash-mode expense paid today
    BigDecimal cashOut,             // − cash_document OUT today
    BigDecimal computedPosition,    // opening + receipts + in − expenses − out

    boolean closed,                 // a cash_day_close exists for (branch, date)
    CashDayCloseResponse close,     // the close row when closed, else null

    List<CashDocumentResponse> movements
) {
}
