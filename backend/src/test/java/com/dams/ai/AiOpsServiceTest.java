package com.dams.ai;

import com.dams.ai.dto.AiOpsDtos.ClaimInsight;
import com.dams.ai.dto.AiOpsDtos.CloseChecklistRow;
import com.dams.ai.service.AiOpsService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.dashboard.dto.BranchComparisonRow;
import com.dams.dashboard.dto.DashboardKpis;
import com.dams.dashboard.dto.DashboardSummary;
import com.dams.dashboard.dto.NamedAmount;
import com.dams.dashboard.dto.OutstandingItem;
import com.dams.dashboard.dto.TrendPoint;
import com.dams.dashboard.service.DashboardService;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FEAT-12 / FEAT-21: old open claims land in the critical bucket with a draft,
 * and the close checklist is ready only when cash is closed and nothing is pending.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiOpsServiceTest {

    private static final long ORG = 1L;

    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private ClaimCloseRepository claimCloseRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private DashboardService dashboardService;
    @Mock private BranchScope branchScope;

    private AiOpsService service;

    @BeforeEach
    void setUp() {
        TenantContext.setOrgId(ORG);
        service = new AiOpsService(receiveDocumentRepo, jobCardRepo, claimCloseRepo,
            customerRepo, branchRepo, dashboardService, branchScope);
        when(branchScope.canSeeBranch(any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void claimInsights_marks100DayOpenClaim_criticalWithDraft() {
        when(claimCloseRepo.findJobCardIdsByOrgId(ORG)).thenReturn(List.of());

        ReceiveDocument doc = new ReceiveDocument();
        doc.setId(21L);
        doc.setBranchId(10L);
        doc.setJobCardId(9L);
        doc.setDocumentNo("OOR-JUL26-R-021");
        doc.setWorkflowStatus(WorkflowStatus.APPROVED);
        doc.setCreatedBy(5L);
        when(receiveDocumentRepo.findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(
            ORG, WorkflowStatus.APPROVED)).thenReturn(List.of(doc));

        JobCard jc = new JobCard();
        jc.setId(9L);
        jc.setBranchId(10L);
        jc.setCustomerId(3L);
        jc.setCategoryId(1L);
        jc.setClaimTypeId(5L);
        jc.setBusinessStatusId(6L);
        jc.setInvoiceAmount(new BigDecimal("12000"));
        jc.setDbmId("DBM-77");
        jc.setCreatedAt(Instant.now().minus(100, ChronoUnit.DAYS));
        when(jobCardRepo.findByOrgIdAndIdIn(ORG, List.of(9L))).thenReturn(List.of(jc));

        Customer customer = new Customer();
        customer.setId(3L);
        customer.setName("ABC Transport");
        when(customerRepo.findByOrgIdAndIdInOrderByNameAsc(ORG, java.util.Set.of(3L)))
            .thenReturn(List.of(customer));

        Branch branch = new Branch();
        branch.setId(10L);
        branch.setCode("OOR");
        when(branchRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of(branch));

        List<ClaimInsight> insights = service.claimInsights(null);

        assertThat(insights).hasSize(1);
        assertThat(insights.get(0).bucket()).isEqualTo("90+");
        assertThat(insights.get(0).draftFollowUp()).contains("OOR-JC-9").contains("DBM-77");
    }

    @Test
    void closeChecklist_readyOnly_whenCashClosedAndNothingPending() {
        when(dashboardService.outstanding(null)).thenReturn(List.of());
        when(dashboardService.summary(null, "mtd")).thenReturn(new DashboardSummary("ALL", "mtd",
            new DashboardKpis(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, 0L),
            List.<TrendPoint>of(), List.<NamedAmount>of(), List.<NamedAmount>of(),
            List.of(new BranchComparisonRow(10L, "OOR", "OOR Branch",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")), BigDecimal.ZERO, 0L))));

        List<CloseChecklistRow> rows = service.closeChecklist(null);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).readyToClose()).isTrue();
    }
}
