package com.dams.audit.service;

import com.dams.audit.dto.OverrideAuditEntry;
import com.dams.audit.entity.AuditEvent;
import com.dams.audit.entity.EventType;
import com.dams.audit.repository.AuditEventRepository;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.config.TenantContext;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.repository.AppUserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The org-wide Override Audit (AGENT.md decision #4): every amount override across the
 * organisation in one feed — the Accountant's provisional line overrides (OVERRIDE audit
 * rows) AND the Finance Manager's final claim-close overrides ({@code claim_close.overridden}).
 * Filterable by branch, actor and date.
 */
@Service
public class OverrideAuditService {

    private static final Logger log = LoggerFactory.getLogger(OverrideAuditService.class);

    private final AuditEventRepository auditEventRepo;
    private final ClaimCloseRepository claimCloseRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final JobCardRepository jobCardRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final BranchRepository branchRepo;
    private final AppUserRepository userRepo;
    private final ObjectMapper objectMapper;

    public OverrideAuditService(AuditEventRepository auditEventRepo,
                                ClaimCloseRepository claimCloseRepo,
                                ReceiveDocumentRepository receiveDocumentRepo,
                                ExpenseDocumentRepository expenseDocumentRepo,
                                JobCardRepository jobCardRepo,
                                SettlementLineRepository settlementLineRepo,
                                BranchRepository branchRepo,
                                AppUserRepository userRepo,
                                ObjectMapper objectMapper) {
        this.auditEventRepo = auditEventRepo;
        this.claimCloseRepo = claimCloseRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.jobCardRepo = jobCardRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<OverrideAuditEntry> list(Instant from, Instant to, Long branchId, Long actorId) {
        Long orgId = TenantContext.requireOrgId();
        Map<Long, String> branchCodes = new HashMap<>();
        Map<Long, String> userNames = new HashMap<>();

        List<OverrideAuditEntry> out = new ArrayList<>();
        out.addAll(lineOverrides(orgId, from, to, branchId, actorId, branchCodes, userNames));
        out.addAll(claimOverrides(orgId, from, to, branchId, actorId, branchCodes, userNames));
        out.sort(Comparator.comparing(OverrideAuditEntry::at).reversed());
        return out;
    }

    // ---- Accountant line overrides (OVERRIDE audit events) ----

    private List<OverrideAuditEntry> lineOverrides(Long orgId, Instant from, Instant to, Long branchId, Long actorId,
                                                   Map<Long, String> branchCodes, Map<Long, String> userNames) {
        List<AuditEvent> events = auditEventRepo.findForOverrideAudit(orgId, EventType.OVERRIDE, from, to, branchId, actorId);
        List<OverrideAuditEntry> out = new ArrayList<>(events.size());
        for (AuditEvent e : events) {
            Map<String, Object> d = detail(e);
            boolean receipt = "ReceiveDocument".equals(e.getEntityType());
            String documentNo = receipt
                ? receiveDocumentRepo.findByIdAndOrgId(e.getEntityId(), orgId).map(x -> x.getDocumentNo()).orElse(null)
                : expenseDocumentRepo.findByIdAndOrgId(e.getEntityId(), orgId).map(x -> x.getDocumentNo()).orElse(null);
            out.add(new OverrideAuditEntry(
                e.getCreatedAt(),
                receipt ? "receipt" : "expense",
                actorName(e.getActorId(), userNames),
                e.getBranchId(),
                branchCode(orgId, e.getBranchId(), branchCodes),
                documentNo,
                str(d.get("lineId")),
                num(d.get("amountBefore")),
                num(d.get("amountAfter")),
                str(d.get("reason"))));
        }
        return out;
    }

    // ---- Finance Manager claim-close overrides ----

    private List<OverrideAuditEntry> claimOverrides(Long orgId, Instant from, Instant to, Long branchId, Long actorId,
                                                    Map<Long, String> branchCodes, Map<Long, String> userNames) {
        List<ClaimClose> closes = claimCloseRepo.findByOrgIdAndOverriddenTrueAndClosedAtBetweenOrderByClosedAtDesc(orgId, from, to);
        List<OverrideAuditEntry> out = new ArrayList<>();
        for (ClaimClose cc : closes) {
            if (actorId != null && !actorId.equals(cc.getClosedBy())) {
                continue;
            }
            JobCard jc = jobCardRepo.findByIdAndOrgId(cc.getJobCardId(), orgId).orElse(null);
            if (jc == null) {
                continue;
            }
            if (branchId != null && !branchId.equals(jc.getBranchId())) {
                continue;
            }
            BigDecimal received = settlementLineRepo.sumAmountForJobCard(orgId, cc.getJobCardId());
            String code = branchCode(orgId, jc.getBranchId(), branchCodes);
            out.add(new OverrideAuditEntry(
                cc.getClosedAt(),
                "claim",
                actorName(cc.getClosedBy(), userNames),
                jc.getBranchId(),
                code,
                code + "-JC-" + jc.getId(),
                null,
                received,
                cc.getFinalAmount(),
                cc.getOverrideReason()));
        }
        return out;
    }

    // ---- helpers ----

    private Map<String, Object> detail(AuditEvent e) {
        if (e.getDetail() == null || e.getDetail().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(e.getDetail(), new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Could not parse OVERRIDE audit detail for event #{}: {}", e.getId(), ex.getMessage());
            return Map.of();
        }
    }

    private String branchCode(Long orgId, Long branchId, Map<Long, String> cache) {
        if (branchId == null) {
            return null;
        }
        return cache.computeIfAbsent(branchId,
            id -> branchRepo.findByIdAndOrgId(id, orgId).map(Branch::getCode).orElse("?"));
    }

    private String actorName(Long actorId, Map<Long, String> cache) {
        if (actorId == null) {
            return "System";
        }
        return cache.computeIfAbsent(actorId,
            id -> userRepo.findById(id).map(AppUser::getName).orElse("User #" + id));
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static BigDecimal num(Object v) {
        return v == null ? null : new BigDecimal(String.valueOf(v));
    }
}
