package com.dams.dashboard.service;

import com.dams.common.time.OrgTime;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Closed claims in Collections (rev 73). The Owner dashboard counts a receipt's approved payment
 * lines on their own dates; once the Finance Manager closes the claim, what was really recovered is
 * the <b>final amount</b>. This service works out, per closed claim, the difference
 * ({@code final − approved lines}) and the day it was closed, so Collections can add it as one
 * labelled "Claim final amount adjustment". It is never cash — the drawer is untouched.
 */
@Service
public class ClaimAdjustmentService {

    /**
     * @param amount signed: negative when the claim was closed below what the Cashier entered,
     *               positive when above
     */
    public record Adjustment(Long jobCardId, Long branchId, Long customerId, LocalDate date, Instant closedAt,
                             BigDecimal finalAmount, BigDecimal approvedLines, BigDecimal amount,
                             Long documentId, String documentNo) {

        /** "Claim closed at ₹5,200 · payment lines ₹1,000". */
        public String describe() {
            return "Claim closed at " + inr(finalAmount) + " · payment lines " + inr(approvedLines);
        }
    }

    private final ClaimCloseRepository claimCloseRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final JobCardRepository jobCardRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;

    public ClaimAdjustmentService(ClaimCloseRepository claimCloseRepo,
                                  SettlementLineRepository settlementLineRepo,
                                  JobCardRepository jobCardRepo,
                                  ReceiveDocumentRepository receiveDocumentRepo) {
        this.claimCloseRepo = claimCloseRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.jobCardRepo = jobCardRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
    }

    /** Every non-zero adjustment for claims closed on a date within {@code from}..{@code to} (branch-local days). */
    @Transactional(readOnly = true)
    public List<Adjustment> between(Long orgId, LocalDate from, LocalDate to) {
        List<ClaimClose> closes = claimCloseRepo.findByOrgId(orgId).stream()
            .filter(c -> {
                LocalDate d = c.getClosedAt().atZone(OrgTime.ZONE).toLocalDate();
                return !d.isBefore(from) && !d.isAfter(to);
            })
            .toList();
        if (closes.isEmpty()) {
            return List.of();
        }
        List<Long> jobCardIds = closes.stream().map(ClaimClose::getJobCardId).toList();
        Map<Long, JobCard> jobCards = jobCardRepo.findByOrgIdAndIdIn(orgId, jobCardIds).stream()
            .collect(Collectors.toMap(JobCard::getId, j -> j, (a, b) -> a));
        Map<Long, BigDecimal> approvedLines = new HashMap<>();
        for (Object[] r : settlementLineRepo.sumApprovedByJobCard(orgId)) {
            approvedLines.put(((Number) r[0]).longValue(), (BigDecimal) r[1]);
        }
        // the newest APPROVED document of each claim — the row in the drill-down opens it
        Map<Long, ReceiveDocument> latestApproved = new HashMap<>();
        for (ReceiveDocument d : receiveDocumentRepo.findByOrgIdAndJobCardIdInOrderByCreatedAtDesc(orgId, jobCardIds)) {
            if (d.getWorkflowStatus() == WorkflowStatus.APPROVED) {
                latestApproved.putIfAbsent(d.getJobCardId(), d);
            }
        }

        List<Adjustment> out = new ArrayList<>();
        for (ClaimClose c : closes) {
            JobCard jc = jobCards.get(c.getJobCardId());
            ReceiveDocument doc = latestApproved.get(c.getJobCardId());
            if (jc == null || doc == null) {
                continue;   // nothing was counted in Collections for it, so there is nothing to correct
            }
            BigDecimal lines = approvedLines.getOrDefault(c.getJobCardId(), BigDecimal.ZERO);
            BigDecimal diff = c.getFinalAmount().subtract(lines);
            if (diff.signum() == 0) {
                continue;
            }
            out.add(new Adjustment(jc.getId(), jc.getBranchId(), jc.getCustomerId(),
                c.getClosedAt().atZone(OrgTime.ZONE).toLocalDate(), c.getClosedAt(),
                c.getFinalAmount(), lines, diff, doc.getId(), doc.getDocumentNo()));
        }
        return out;
    }

    /** ₹ with Indian digit grouping, rounded to the rupee: 1234567 → ₹12,34,567. */
    static String inr(BigDecimal v) {
        long n = v.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        String digits = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        int len = digits.length();
        if (len <= 3) {
            sb.append(digits);
        } else {
            sb.append(digits, 0, len - 3);
            String head = sb.toString();
            StringBuilder grouped = new StringBuilder();
            for (int i = 0; i < head.length(); i++) {
                if (i > 0 && (head.length() - i) % 2 == 0) {
                    grouped.append(',');
                }
                grouped.append(head.charAt(i));
            }
            sb = new StringBuilder(grouped).append(',').append(digits.substring(len - 3));
        }
        return (n < 0 ? "−₹" : "₹") + sb;
    }
}
