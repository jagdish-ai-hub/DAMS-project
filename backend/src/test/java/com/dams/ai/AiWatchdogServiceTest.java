package com.dams.ai;

import com.dams.ai.dto.AiWatchdogDtos.QueryRoot;
import com.dams.ai.dto.AiWatchdogDtos.RiskScore;
import com.dams.ai.service.AiWatchdogService;
import com.dams.attachment.repository.AttachmentRepository;
import com.dams.audit.entity.AuditEvent;
import com.dams.audit.entity.EventType;
import com.dams.audit.repository.AuditEventRepository;
import com.dams.audit.service.OverrideAuditService;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.dashboard.service.DashboardService;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * FEAT-16 / FEAT-13: risk scores surface overridden documents first, and query
 * notes cluster into named root causes with counts.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiWatchdogServiceTest {

    private static final long ORG = 1L;

    @Mock private OverrideAuditService overrideAuditService;
    @Mock private AuditEventRepository auditEventRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private AttachmentRepository attachmentRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private DashboardService dashboardService;
    @Mock private BranchScope branchScope;

    private AiWatchdogService service;

    @BeforeEach
    void setUp() {
        TenantContext.setOrgId(ORG);
        service = new AiWatchdogService(overrideAuditService, auditEventRepo,
            receiveDocumentRepo, expenseDocumentRepo, settlementLineRepo, expenseLineRepo,
            attachmentRepo, branchRepo, dashboardService, branchScope, new ObjectMapper());
        when(branchScope.canSeeBranch(any())).thenReturn(true);
        when(branchScope.allowedBranchIds()).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void riskScores_ranksOverriddenDocument_aboveCleanDocument() {
        ReceiveDocument dirty = receipt(1L, "OOR-JUL26-R-021");
        ReceiveDocument clean = receipt(2L, "OOR-JUL26-R-022");
        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(
            ORG, WorkflowStatus.SUBMITTED)).thenReturn(List.of(dirty, clean));
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(
            ORG, List.of(1L, 2L))).thenReturn(List.of(
                overriddenLine(1L), plainLine(2L, new BigDecimal("1000"))));
        when(attachmentRepo.countByParentIdIn(eq(ORG),
            eq(com.dams.attachment.entity.ParentType.RECEIVE_DOCUMENT), eq(List.of(1L, 2L))))
            .thenReturn(List.of(new Object[]{1L, 1L}, new Object[]{2L, 1L}));
        when(branchRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of());

        List<RiskScore> scores = service.riskScores("receipt", null);

        assertThat(scores).hasSize(2);
        assertThat(scores.get(0).documentNo()).isEqualTo("OOR-JUL26-R-021");
        assertThat(String.join(" ", scores.get(0).reasons())).containsIgnoringCase("overridden");
    }

    @Test
    void queryRoots_clustersBillNotes_intoOneCauseWithCount() {
        when(auditEventRepo.findForOverrideAudit(eq(ORG), eq(EventType.QUERIED),
            any(Instant.class), any(Instant.class), eq(null), eq(null)))
            .thenReturn(List.of(
                queriedEvent("{\"note\":\"please attach a clear bill photo\"}"),
                queriedEvent("{\"note\":\"bill photo is blurry, attach again\"}"),
                queriedEvent("{\"note\":\"amount mismatch with bill total\"}")));

        List<QueryRoot> roots = service.queryRoots(null);

        assertThat(roots.get(0).cause()).isEqualTo("Missing or unreadable bill attachment");
        assertThat(roots.get(0).count()).isEqualTo(2);
        assertThat(roots.get(0).suggestion()).isNotBlank();
    }

    private ReceiveDocument receipt(Long id, String documentNo) {
        ReceiveDocument doc = new ReceiveDocument();
        doc.setId(id);
        doc.setBranchId(10L);
        doc.setJobCardId(99L);
        doc.setDocumentNo(documentNo);
        doc.setWorkflowStatus(WorkflowStatus.SUBMITTED);
        doc.setCreatedBy(5L);
        doc.setSubmittedAt(Instant.now());
        return doc;
    }

    private SettlementLine overriddenLine(Long docId) {
        SettlementLine line = new SettlementLine();
        line.setReceiveDocumentId(docId);
        line.setLineNo(1);
        line.setAmount(new BigDecimal("5000"));
        line.setOverriddenBy(6L);
        line.setCreatedBy(5L);
        return line;
    }

    private SettlementLine plainLine(Long docId, BigDecimal amount) {
        SettlementLine line = new SettlementLine();
        line.setReceiveDocumentId(docId);
        line.setLineNo(1);
        line.setAmount(amount);
        line.setCreatedBy(5L);
        return line;
    }

    private AuditEvent queriedEvent(String detail) {
        AuditEvent event = new AuditEvent();
        event.setOrgId(ORG);
        event.setEntityType("ReceiveDocument");
        event.setEntityId(1L);
        event.setEventType(EventType.QUERIED);
        event.setDetail(detail);
        return event;
    }

    @Test
    void riskScores_expenseFlagsOverLimitDocument() {
        ExpenseDocument doc = new ExpenseDocument();
        doc.setId(9L);
        doc.setBranchId(10L);
        doc.setReceiverId(3L);
        doc.setExpenseCategoryId(4L);
        doc.setBusinessStatusId(5L);
        doc.setDocumentNo("OOR-JUL26-E-007");
        doc.setWorkflowStatus(ExpenseWorkflowStatus.SUBMITTED);
        doc.setOverLimit(true);
        doc.setCreatedBy(5L);
        when(expenseDocumentRepo.findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(
            ORG, ExpenseWorkflowStatus.SUBMITTED)).thenReturn(List.of(doc));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(
            ORG, List.of(9L))).thenReturn(List.of());
        when(attachmentRepo.countByParentIdIn(eq(ORG),
            eq(com.dams.attachment.entity.ParentType.EXPENSE_DOCUMENT), eq(List.of(9L))))
            .thenReturn(List.of());
        when(branchRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of());

        List<RiskScore> scores = service.riskScores("expense", null);

        assertThat(scores).hasSize(1);
        assertThat(String.join(" ", scores.get(0).reasons())).containsIgnoringCase("limit");
    }
}
