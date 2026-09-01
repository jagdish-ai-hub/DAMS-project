package com.dams.jobcard.service;

import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.receive.repository.SettlementLineRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The one place Pending Amount is computed. It is job-card-wide, never per-document
 * (plan.md "Pending Amount"):
 *
 *   pending = invoice_amount − Σ(settlement-line amount across ALL the job card's
 *             non-REJECTED receive documents), floored at 0
 *
 * with two hard rules:
 *   - returns 0 when {@code invoice_amount} is null (an advance was taken, no invoice raised);
 *   - returns 0 when a ClaimClose row exists for the job card — the claim is settled at
 *     {@code ClaimClose.final_amount}, and the raw invoice−Σ shortfall must never surface as
 *     a phantom balance (this bug was hit and fixed once in the HTML prototype).
 */
@Component
public class PendingAmountCalculator {

    private final SettlementLineRepository settlementLineRepo;
    private final ClaimCloseRepository claimCloseRepo;

    public PendingAmountCalculator(SettlementLineRepository settlementLineRepo,
                                   ClaimCloseRepository claimCloseRepo) {
        this.settlementLineRepo = settlementLineRepo;
        this.claimCloseRepo = claimCloseRepo;
    }

    public BigDecimal forJobCard(JobCard jobCard) {
        return forJobCard(jobCard.getOrgId(), jobCard.getId(), jobCard.getInvoiceAmount());
    }

    public BigDecimal forJobCard(Long orgId, Long jobCardId, BigDecimal invoiceAmount) {
        // Claim settled at its final amount — no shortfall balance, ever.
        if (claimCloseRepo.existsByOrgIdAndJobCardId(orgId, jobCardId)) {
            return BigDecimal.ZERO;
        }
        // Advance taken, invoice not raised yet — nothing is "pending" against a missing invoice.
        if (invoiceAmount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal received = settlementLineRepo.sumAmountForJobCard(orgId, jobCardId);
        BigDecimal pending = invoiceAmount.subtract(received);
        // An overpayment is not a negative balance.
        return pending.signum() < 0 ? BigDecimal.ZERO : pending;
    }

    /**
     * Pending amount for many job cards at once, in two queries total instead of two per card
     * ({@link #existsByOrgIdAndJobCardId} + the Σ). Same rules as {@link #forJobCard}. Used by
     * list screens (My Entries, the review queues) that would otherwise fan out.
     */
    public Map<Long, BigDecimal> forJobCards(Long orgId, Collection<JobCard> jobCards) {
        Map<Long, BigDecimal> out = new HashMap<>();
        if (jobCards.isEmpty()) {
            return out;
        }
        Set<Long> closed = new HashSet<>(claimCloseRepo.findJobCardIdsByOrgId(orgId));
        Map<Long, BigDecimal> received = new HashMap<>();
        for (Object[] r : settlementLineRepo.sumAmountByJobCard(orgId)) {
            received.put(((Number) r[0]).longValue(), (BigDecimal) r[1]);
        }
        for (JobCard jc : jobCards) {
            if (closed.contains(jc.getId()) || jc.getInvoiceAmount() == null) {
                out.put(jc.getId(), BigDecimal.ZERO);
                continue;
            }
            BigDecimal pending = jc.getInvoiceAmount().subtract(received.getOrDefault(jc.getId(), BigDecimal.ZERO));
            out.put(jc.getId(), pending.signum() < 0 ? BigDecimal.ZERO : pending);
        }
        return out;
    }

    /** True once a claim has been closed for this job card (drives the PATCH 409 guard). */
    public boolean hasClaimClose(Long orgId, Long jobCardId) {
        return claimCloseRepo.existsByOrgIdAndJobCardId(orgId, jobCardId);
    }
}
