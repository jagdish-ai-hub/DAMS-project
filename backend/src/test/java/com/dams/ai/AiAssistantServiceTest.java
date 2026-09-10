package com.dams.ai;

import com.dams.ai.dto.AiAssistantDtos.AiAnswer;
import com.dams.ai.dto.AiAssistantDtos.AiBrief;
import com.dams.ai.entity.AiQueryLog;
import com.dams.ai.repository.AiQueryLogRepository;
import com.dams.ai.service.AiAssistantService;
import com.dams.ai.service.DeterministicInsightService;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.dashboard.dto.ActivityItem;
import com.dams.dashboard.dto.BranchComparisonRow;
import com.dams.dashboard.dto.DashboardKpis;
import com.dams.dashboard.dto.DashboardSummary;
import com.dams.dashboard.dto.NamedAmount;
import com.dams.dashboard.dto.OutstandingItem;
import com.dams.dashboard.dto.TrendPoint;
import com.dams.dashboard.service.DashboardService;
import com.dams.ai.dto.AiOpsDtos.ClaimInsight;
import com.dams.ai.service.AiOpsService;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.entity.CashDirection;
import com.dams.cash.entity.CashWorkflowStatus;
import com.dams.jobcard.entity.JobCard;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FEAT-09 / FEAT-10: the assistant answers only with real aggregate data, cites
 * real document numbers, and leaves a trace row for every answer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiAssistantServiceTest {

    private static final long ORG = 1L;

    @Mock private DashboardService dashboardService;
    @Mock private BranchScope branchScope;
    @Mock private AiQueryLogRepository queryLogRepo;
    @Mock private AiOpsService opsService;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private com.dams.jobcard.repository.JobCardRepository jobCardRepo;
    @Mock private com.dams.customer.repository.CustomerRepository customerRepo;
    @Mock private com.dams.vehicle.repository.VehicleRepository vehicleRepo;
    @Mock private com.dams.masters.repository.ReceiveCategoryRepository receiveCategoryRepo;
    @Mock private com.dams.jobcard.repository.ClaimCloseRepository claimCloseRepo;
    @Mock private com.dams.cash.repository.CashDocumentRepository cashDocumentRepo;
    @Mock private com.dams.ai.service.AiWatchdogService watchdogService;

    private AiAssistantService service;

    @BeforeEach
    void setUp() {
        TenantContext.setOrgId(ORG);
        service = new AiAssistantService(dashboardService,
            branchScope, queryLogRepo, new DeterministicInsightService(), opsService,
            receiveDocumentRepo, expenseDocumentRepo, settlementLineRepo,
            expenseLineRepo, branchRepo,
            jobCardRepo, customerRepo, vehicleRepo, receiveCategoryRepo,
            claimCloseRepo, cashDocumentRepo, watchdogService);
        when(branchScope.canSeeBranch(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ask_citesRealDocumentNumbers_fromOutstanding() {
        when(dashboardService.summary(null, "mtd")).thenReturn(summary());
        when(dashboardService.outstanding(null)).thenReturn(List.of(
            new OutstandingItem("claim", "ABC Transport", "KA01 · OOR · ref",
                new BigDecimal("12000"), "OOR-JUL26-R-021", "OOR")));
        when(branchScope.currentUserId()).thenReturn(7L);
        when(queryLogRepo.save(any(AiQueryLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AiAnswer answer = service.ask("which claims are open?", null);

        assertThat(answer.answer()).contains("OOR-JUL26-R-021");
        assertThat(answer.citedDocs()).contains("OOR-JUL26-R-021");
        verify(queryLogRepo).save(any(AiQueryLog.class));
    }

    @Test
    void ask_rejectsBlankQuestion_withNamedError() {
        assertThatThrownBy(() -> service.ask("   ", null))
            .hasMessageContaining("Question must not be empty");
    }

    @Test
    void ask_resolvesNamedDocument_toRealDocumentFacts() {
        when(dashboardService.summary(null, "mtd")).thenReturn(summary());
        when(dashboardService.outstanding(null)).thenReturn(List.of());
        ReceiveDocument doc = new ReceiveDocument();
        doc.setId(21L);
        doc.setBranchId(10L);
        doc.setJobCardId(9L);
        doc.setDocumentNo("OOR-JUL26-R-021");
        doc.setWorkflowStatus(WorkflowStatus.SUBMITTED);
        doc.setCreatedBy(5L);
        when(receiveDocumentRepo.findByOrgIdAndDocumentNoContainingIgnoreCase(ORG, "OOR-JUL26-R-021"))
            .thenReturn(List.of(doc));
        SettlementLine line = new SettlementLine();
        line.setReceiveDocumentId(21L);
        line.setLineNo(1);
        line.setAmount(new BigDecimal("5000"));
        line.setCreatedBy(5L);
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(ORG, 21L))
            .thenReturn(List.of(line));
        Branch branch = new Branch();
        branch.setId(10L);
        branch.setCode("OOR");
        when(branchRepo.findByIdAndOrgId(10L, ORG)).thenReturn(java.util.Optional.of(branch));
        when(branchScope.currentUserId()).thenReturn(7L);
        when(queryLogRepo.save(any(AiQueryLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AiAnswer answer = service.ask("tell me about OOR-JUL26-R-021", null);

        assertThat(answer.answer()).contains("OOR-JUL26-R-021").contains("SUBMITTED");
        assertThat(answer.citedDocs()).containsExactly("OOR-JUL26-R-021");
    }

    @Test
    void brief_carriesVerifiedKpiNumbers_andBullets() {
        when(dashboardService.summary(null, "mtd")).thenReturn(summary());
        when(dashboardService.outstanding(null)).thenReturn(List.of());
        when(dashboardService.activity(null, 5)).thenReturn(List.of());

        AiBrief brief = service.brief("mtd", null);

        assertThat(brief.collections()).isEqualByComparingTo("50000");
        assertThat(brief.expenses()).isEqualByComparingTo("20000");
        assertThat(brief.bullets()).isNotEmpty();
    }

    private DashboardSummary summary() {
        return new DashboardSummary("ALL", "mtd",
            new DashboardKpis(new BigDecimal("50000"), new BigDecimal("20000"),
                new BigDecimal("30000"), new BigDecimal("10000"), 3L),
            List.of(), List.<NamedAmount>of(), List.<NamedAmount>of(),
            List.<BranchComparisonRow>of());
    }

    @Test
    void brief_latestActivity_mentionsMostRecentAction() {
        when(dashboardService.summary(null, "mtd")).thenReturn(summary());
        when(dashboardService.outstanding(null)).thenReturn(List.of());
        when(dashboardService.activity(null, 5)).thenReturn(List.of(
            new ActivityItem("Meera", "Approved", "OOR-JUL26-R-021", "ABC Transport",
                new BigDecimal("5000"), "OOR", java.time.Instant.now())));

        AiBrief brief = service.brief("mtd", null);

        assertThat(String.join(" ", brief.bullets())).contains("OOR-JUL26-R-021");
    }

    @Test
    void brief_countsCriticalClaims_fromRealBuckets() {
        when(dashboardService.summary(null, "mtd")).thenReturn(summary());
        when(dashboardService.outstanding(null)).thenReturn(List.of(
            new OutstandingItem("claim", "ABC Transport", "OOR · Warranty · awaiting Eicher settlement",
                new BigDecimal("12000"), "OOR-JUL26-R-021", "OOR")));
        when(dashboardService.activity(null, 5)).thenReturn(List.of());
        when(opsService.claimInsights(null)).thenReturn(List.of(
            new ClaimInsight("OOR-JUL26-R-021", "OOR", "ABC Transport",
                new BigDecimal("12000"), 100, "90+", "draft")));

        AiBrief brief = service.brief("mtd", null);

        assertThat(String.join(" ", brief.bullets())).contains("1 critical 90+ days");
    }

    @Test
    void ask_unknownDocument_citesNothing() {
        when(branchScope.currentUserId()).thenReturn(7L);
        when(queryLogRepo.save(any(AiQueryLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AiAnswer answer = service.ask("tell me about OOR-JUL26-R-999", null);

        assertThat(answer.answer()).contains("No receive or expense document OOR-JUL26-R-999");
        assertThat(answer.citedDocs()).isEmpty();
    }

    @Test
    void ask_resolvesJobCard_toRealDatabaseFacts() {
        JobCard jc = new JobCard();
        jc.setId(7L);
        jc.setBranchId(10L);
        jc.setCustomerId(50L);
        jc.setCategoryId(2L);
        jc.setInvoiceNo("INV-777");
        jc.setInvoiceAmount(new BigDecimal("25000"));
        when(jobCardRepo.findByIdAndOrgId(7L, ORG)).thenReturn(java.util.Optional.of(jc));

        Branch branch = new Branch();
        branch.setId(10L);
        branch.setCode("OOJ");
        when(branchRepo.findByIdAndOrgId(10L, ORG)).thenReturn(java.util.Optional.of(branch));

        com.dams.customer.entity.Customer cust = new com.dams.customer.entity.Customer();
        cust.setId(50L);
        cust.setName("Aravind Traders");
        when(customerRepo.findByIdAndOrgId(50L, ORG)).thenReturn(java.util.Optional.of(cust));

        com.dams.masters.entity.ReceiveCategory cat = new com.dams.masters.entity.ReceiveCategory();
        cat.setId(2L);
        cat.setName("Workshop");
        cat.setClaim(false);
        when(receiveCategoryRepo.findByIdAndOrgId(2L, ORG)).thenReturn(java.util.Optional.of(cat));

        when(receiveDocumentRepo.findByOrgIdAndJobCardIdOrderByCreatedAtDesc(ORG, 7L)).thenReturn(List.of());
        when(branchScope.currentUserId()).thenReturn(7L);
        when(queryLogRepo.save(any(AiQueryLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AiAnswer answer = service.ask("Tell me about OOJ-JC-7", null);

        assertThat(answer.answer()).contains("Job Card OOJ-JC-7").contains("Aravind Traders").contains("INV-777");
        assertThat(answer.citedDocs()).containsExactly("OOJ-JC-7");
    }

    @Test
    void ask_resolvesCashDocument_toRealDatabaseFacts() {
        CashDocument cash = new CashDocument();
        cash.setId(5L);
        cash.setBranchId(10L);
        cash.setDocumentNo("OOR-JUL26-C-005");
        cash.setDirection(CashDirection.OUT);
        cash.setAmount(new BigDecimal("15000"));
        cash.setTransactionDate(java.time.LocalDate.now());
        cash.setWorkflowStatus(CashWorkflowStatus.APPROVED);
        cash.setTransactionRef("HDFC-REF-001");
        when(cashDocumentRepo.findByOrgIdAndDocumentNoIgnoreCase(ORG, "OOR-JUL26-C-005"))
            .thenReturn(java.util.Optional.of(cash));

        Branch branch = new Branch();
        branch.setId(10L);
        branch.setCode("OOR");
        when(branchRepo.findByIdAndOrgId(10L, ORG)).thenReturn(java.util.Optional.of(branch));
        when(branchScope.currentUserId()).thenReturn(7L);
        when(queryLogRepo.save(any(AiQueryLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AiAnswer answer = service.ask("What is OOR-JUL26-C-005?", null);

        assertThat(answer.answer()).contains("Cash movement OOR-JUL26-C-005").contains("15000").contains("HDFC-REF-001");
        assertThat(answer.citedDocs()).containsExactly("OOR-JUL26-C-005");
    }
}
