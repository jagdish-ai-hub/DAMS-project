package com.dams.recon.dto;

import com.dams.recon.entity.ReconLine;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReconLineResponse(
    Long id,
    LocalDate txnDate,
    String utr,
    BigDecimal amount,
    String narration,
    Long matchedSettlementLineId,
    String matchedDocumentNo,
    String matchKind,
    boolean ignored,
    boolean resolved
) {
    public static ReconLineResponse of(ReconLine l, String matchedDocumentNo) {
        return new ReconLineResponse(
            l.getId(), l.getTxnDate(), l.getUtr(), l.getAmount(), l.getNarration(),
            l.getMatchedSettlementLineId(), matchedDocumentNo, l.getMatchKind(),
            l.isIgnored(), l.isResolved());
    }
}
