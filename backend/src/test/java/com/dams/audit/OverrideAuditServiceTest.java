package com.dams.audit;

import com.dams.audit.dto.OverrideAuditEntry;
import com.dams.audit.entity.ActorType;
import com.dams.audit.entity.AuditEvent;
import com.dams.audit.entity.EventType;
import com.dams.audit.repository.AuditEventRepository;
import com.dams.audit.service.OverrideAuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.config.TenantContext;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.repository.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The Override Audit feed merges the Accountant's line overrides (OVERRIDE audit rows) and
 * the Finance Manager's claim-close overrides ({@code claim_close.overridden}) into one
 * newest-first list.
 */
@ExtendWith(MockitoExtension.class)
class OverrideAuditServiceTest {

    private static final long ORG = 1L;
    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-01T00:00:00Z");

    @Mock private AuditEventRepository auditEventRepo;
    @Mock private ClaimCloseRepository claimCloseRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private AppUserRepository userRepo;

    private OverrideAuditService service;

    @BeforeEach
    void setUp() {
        service = new OverrideAuditService(auditEventRepo, claimCloseRepo, receiveDocumentRepo, expenseDocumentRepo,
            jobCardRepo, settlementLineRepo, branchRepo, userRepo, new ObjectMapper());
        TenantContext.setOrgId(ORG);
        lenient().when(branchRepo.findByIdAndOrgId(eq(3L), eq(ORG))).thenReturn(Optional.of(branch(3L, "OOR")));
        lenient().when(userRepo.findById(any())).thenReturn(Optional.of(user("Sunita Padhi")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_mergesLineAndClaimOverrides_newestFirst() {
        AuditEvent lineOverride = overrideEvent(
            "ReceiveDocument", 16L, 3L, Instant.parse("2026-07-20T10:00:00Z"),
            "{\"lineId\":\"OOR-JUL26-R-016-L1\",\"amountBefore\":800,\"amountAfter\":1000,\"reason\":\"Rate corrected\"}");
        when(auditEventRepo.findForOverrideAudit(ORG, EventType.OVERRIDE, FROM, TO, null, null))
            .thenReturn(List.of(lineOverride));
        when(receiveDocumentRepo.findByIdAndOrgId(16L, ORG)).thenReturn(Optional.of(receiveDoc(16L, "OOR-JUL26-R-016")));

        ClaimClose cc = claimClose(70L, new BigDecimal("12400"), Instant.parse("2026-07-25T09:00:00Z"), "Partial settlement");
        when(claimCloseRepo.findByOrgIdAndOverriddenTrueAndClosedAtBetweenOrderByClosedAtDesc(ORG, FROM, TO))
            .thenReturn(List.of(cc));
        when(jobCardRepo.findByIdAndOrgId(70L, ORG)).thenReturn(Optional.of(jobCard(70L, 3L)));
        when(settlementLineRepo.sumAmountForJobCard(ORG, 70L)).thenReturn(new BigDecimal("13000"));

        List<OverrideAuditEntry> out = service.list(FROM, TO, null, null);

        assertThat(out).hasSize(2);
        // claim close (7/25) is newer than the line override (7/20)
        assertThat(out.get(0).kind()).isEqualTo("claim");
        assertThat(out.get(0).amountBefore()).isEqualByComparingTo("13000");
        assertThat(out.get(0).amountAfter()).isEqualByComparingTo("12400");
        assertThat(out.get(0).documentNo()).isEqualTo("OOR-JC-70");
        assertThat(out.get(0).lineId()).isNull();

        assertThat(out.get(1).kind()).isEqualTo("receipt");
        assertThat(out.get(1).documentNo()).isEqualTo("OOR-JUL26-R-016");
        assertThat(out.get(1).lineId()).isEqualTo("OOR-JUL26-R-016-L1");
        assertThat(out.get(1).amountBefore()).isEqualByComparingTo("800");
        assertThat(out.get(1).amountAfter()).isEqualByComparingTo("1000");
        assertThat(out.get(1).reason()).isEqualTo("Rate corrected");
    }

    @Test
    void list_excludesClaimOverride_whenBranchFilterDoesNotMatch() {
        when(auditEventRepo.findForOverrideAudit(ORG, EventType.OVERRIDE, FROM, TO, 9L, null)).thenReturn(List.of());
        ClaimClose cc = claimClose(70L, new BigDecimal("100"), Instant.parse("2026-07-25T09:00:00Z"), "x");
        when(claimCloseRepo.findByOrgIdAndOverriddenTrueAndClosedAtBetweenOrderByClosedAtDesc(ORG, FROM, TO))
            .thenReturn(List.of(cc));
        when(jobCardRepo.findByIdAndOrgId(70L, ORG)).thenReturn(Optional.of(jobCard(70L, 3L))); // branch 3, filter asks 9

        assertThat(service.list(FROM, TO, 9L, null)).isEmpty();
    }

    // ---- fixtures ----

    private static AuditEvent overrideEvent(String entityType, long entityId, Long branchId, Instant at, String detail) {
        AuditEvent e = new AuditEvent();
        ReflectionTestUtils.setField(e, "id", 1L);
        e.setOrgId(ORG);
        e.setEntityType(entityType);
        e.setEntityId(entityId);
        e.setBranchId(branchId);
        e.setEventType(EventType.OVERRIDE);
        e.setActorType(ActorType.USER);
        e.setActorId(50L);
        e.setDetail(detail);
        e.setCreatedAt(at);
        return e;
    }

    private static ReceiveDocument receiveDoc(long id, String docNo) {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", id);
        d.setOrgId(ORG);
        d.setBranchId(3L);
        d.setJobCardId(70L);
        d.setDocumentNo(docNo);
        d.setCreatedBy(7L);
        return d;
    }

    private static ClaimClose claimClose(long jobCardId, BigDecimal finalAmount, Instant closedAt, String reason) {
        ClaimClose cc = new ClaimClose();
        ReflectionTestUtils.setField(cc, "id", 1L);
        cc.setOrgId(ORG);
        cc.setJobCardId(jobCardId);
        cc.setFinalAmount(finalAmount);
        cc.setOverridden(true);
        cc.setOverrideReason(reason);
        cc.setClosedBy(60L);
        ReflectionTestUtils.setField(cc, "closedAt", closedAt);
        return cc;
    }

    private static JobCard jobCard(long id, long branchId) {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", id);
        jc.setOrgId(ORG);
        jc.setBranchId(branchId);
        jc.setCustomerId(42L);
        jc.setCategoryId(9L);
        jc.setBusinessStatusId(5L);
        return jc;
    }

    private static Branch branch(long id, String code) {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", id);
        b.setCode(code);
        b.setName(code + " branch");
        return b;
    }

    private static AppUser user(String name) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", 50L);
        u.setName(name);
        return u;
    }
}
