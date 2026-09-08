package com.dams.estimate;

import com.dams.audit.service.AuditService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.estimate.dto.CreateEstimateRequest;
import com.dams.estimate.dto.EstimateResponse;
import com.dams.estimate.entity.Estimate;
import com.dams.estimate.entity.EstimateLine;
import com.dams.estimate.repository.EstimateLineRepository;
import com.dams.estimate.repository.EstimateRepository;
import com.dams.estimate.service.EstimateService;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FEAT-48: quotes before work, variance vs the final bill. Re-quoting
 * supersedes (history survives); only DRAFT estimates can be decided.
 */
@ExtendWith(MockitoExtension.class)
class EstimateServiceTest {

    private static final long ORG = 1L;
    private static final long ACTOR = 50L;
    private static final long JOB = 11L;
    private static final long BRANCH = 3L;

    @Mock private EstimateRepository estimateRepo;
    @Mock private EstimateLineRepository lineRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private BranchScope branchScope;
    @Mock private AuditService auditService;

    private EstimateService service;

    @BeforeEach
    void setUp() {
        service = new EstimateService(estimateRepo, lineRepo, jobCardRepo, branchScope, auditService);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.currentUserId()).thenReturn(ACTOR);
        lenient().when(branchScope.canSeeBranch(BRANCH)).thenReturn(true);
        lenient().when(jobCardRepo.findByIdAndOrgId(JOB, ORG)).thenReturn(Optional.of(jobCard()));
        lenient().when(estimateRepo.save(any(Estimate.class))).thenAnswer(i -> {
            Estimate e = i.getArgument(0);
            if (e.getId() == null) ReflectionTestUtils.setField(e, "id", 400L);
            return e;
        });
        lenient().when(lineRepo.save(any(EstimateLine.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(lineRepo.findByOrgIdAndEstimateIdOrderByLineNoAsc(ORG, 400L)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_totalsLines_andStartsDraft() {
        when(estimateRepo.findByOrgIdAndJobCardIdOrderByCreatedAtDesc(ORG, JOB)).thenReturn(List.of());

        EstimateResponse r = service.create(request());

        assertThat(r.status()).isEqualTo(Estimate.DRAFT);
        assertThat(r.total()).isEqualByComparingTo("10000");
        // Fixture job is already billed at 12000 — variance shows immediately.
        assertThat(r.varianceVsInvoice()).isEqualByComparingTo("2000");
    }

    @Test
    void create_supersedesLiveEstimates_keepingHistory() {
        Estimate live = estimate(401L, Estimate.APPROVED);
        when(estimateRepo.findByOrgIdAndJobCardIdOrderByCreatedAtDesc(ORG, JOB)).thenReturn(List.of(live));

        service.create(request());

        assertThat(live.getStatus()).isEqualTo(Estimate.SUPERSEDED);
        verify(estimateRepo).save(live);
    }

    @Test
    void decide_approve_thenVarianceShowsAgainstInvoice() {
        Estimate draft = estimate(400L, Estimate.DRAFT);
        when(estimateRepo.findByIdAndOrgId(400L, ORG)).thenReturn(Optional.of(draft));

        EstimateResponse r = service.decide(400L, true, null);

        assertThat(r.status()).isEqualTo(Estimate.APPROVED);
        assertThat(r.approvedBy()).isEqualTo(ACTOR);
        // Invoice 12000 − quote 10000 = +2000 over.
        assertThat(r.varianceVsInvoice()).isEqualByComparingTo("2000");
    }

    @Test
    void decide_refusesNonDraft() {
        Estimate approved = estimate(401L, Estimate.APPROVED);
        when(estimateRepo.findByIdAndOrgId(401L, ORG)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.decide(401L, true, null))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("already APPROVED");
    }

    // ---------------------------------------------------------- fixtures

    private static CreateEstimateRequest request() {
        CreateEstimateRequest r = new CreateEstimateRequest();
        r.setJobCardId(JOB);
        CreateEstimateRequest.Line l1 = new CreateEstimateRequest.Line();
        l1.setDescription("Clutch plate");
        l1.setAmount(new BigDecimal("7000"));
        CreateEstimateRequest.Line l2 = new CreateEstimateRequest.Line();
        l2.setDescription("Labour");
        l2.setAmount(new BigDecimal("3000"));
        r.setLines(List.of(l1, l2));
        return r;
    }

    private static Estimate estimate(Long id, String status) {
        Estimate e = new Estimate();
        ReflectionTestUtils.setField(e, "id", id);
        e.setOrgId(ORG);
        e.setJobCardId(JOB);
        e.setStatus(status);
        e.setTotal(new BigDecimal("10000"));
        return e;
    }

    private static JobCard jobCard() {
        JobCard j = new JobCard();
        ReflectionTestUtils.setField(j, "id", JOB);
        j.setOrgId(ORG);
        j.setBranchId(BRANCH);
        j.setCustomerId(5L);
        j.setInvoiceAmount(new BigDecimal("12000"));
        return j;
    }
}
