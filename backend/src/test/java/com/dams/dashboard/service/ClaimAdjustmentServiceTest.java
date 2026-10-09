package com.dams.dashboard.service;

import com.dams.config.TenantContext;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** rev 73 — closed claims in Collections: (final − approved lines), on the close day (India time). */
@ExtendWith(MockitoExtension.class)
class ClaimAdjustmentServiceTest {

    private static final long ORG = 1L;

    @Mock private ClaimCloseRepository claimCloseRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;

    private ClaimAdjustmentService service;

    @BeforeEach
    void setUp() {
        service = new ClaimAdjustmentService(claimCloseRepo, settlementLineRepo, jobCardRepo, receiveDocumentRepo);
        TenantContext.setOrgId(ORG);
    }

    private ClaimClose close(long jobCardId, String finalAmount, Instant at) {
        ClaimClose c = new ClaimClose();
        c.setOrgId(ORG);
        c.setJobCardId(jobCardId);
        c.setFinalAmount(new BigDecimal(finalAmount));
        ReflectionTestUtils.setField(c, "closedAt", at);
        return c;
    }

    private JobCard jobCard(long id) {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", id);
        jc.setOrgId(ORG);
        jc.setBranchId(3L);
        jc.setCustomerId(21L);
        return jc;
    }

    private ReceiveDocument doc(long id, long jobCardId, WorkflowStatus status) {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", id);
        d.setOrgId(ORG);
        d.setJobCardId(jobCardId);
        d.setDocumentNo("OOR-SEP26-R-0" + id);
        d.setWorkflowStatus(status);
        return d;
    }

    private void given(List<ClaimClose> closes, long jobCardId, String approvedLines, ReceiveDocument... docs) {
        when(claimCloseRepo.findByOrgId(ORG)).thenReturn(closes);
        lenient().when(jobCardRepo.findByOrgIdAndIdIn(any(), any())).thenReturn(List.of(jobCard(jobCardId)));
        lenient().when(settlementLineRepo.sumApprovedByJobCard(ORG))
            .thenReturn(List.<Object[]>of(new Object[]{jobCardId, new BigDecimal(approvedLines)}));
        lenient().when(receiveDocumentRepo.findByOrgIdAndJobCardIdInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of(docs));
    }

    private static final Instant SEP_16_NOON = Instant.parse("2026-09-16T06:30:00Z");

    @Test
    void closedBelowTheLines_isANegativeAdjustment_onTheCloseDay() {
        given(List.of(close(24, "15000", SEP_16_NOON)), 24, "16500", doc(8, 24, WorkflowStatus.APPROVED));

        var out = service.between(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).amount()).isEqualByComparingTo("-1500");
        assertThat(out.get(0).date()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(out.get(0).documentNo()).isEqualTo("OOR-SEP26-R-08");
        assertThat(out.get(0).describe()).isEqualTo("Claim closed at ₹15,000 · payment lines ₹16,500");
    }

    @Test
    void closedAboveTheLines_isAPositiveAdjustment() {
        given(List.of(close(95, "5200", SEP_16_NOON)), 95, "1000", doc(36, 95, WorkflowStatus.APPROVED));

        assertThat(service.between(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).get(0).amount())
            .isEqualByComparingTo("4200");
    }

    @Test
    void closedExactlyAtTheLines_needsNoAdjustment() {
        given(List.of(close(23, "21000", SEP_16_NOON)), 23, "21000", doc(22, 23, WorkflowStatus.APPROVED));

        assertThat(service.between(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).isEmpty();
    }

    @Test
    void theCloseDayIsTheIndianDay_notTheUtcDay() {
        // 19:00 UTC on 8 Oct is 00:30 on 9 Oct in India
        given(List.of(close(24, "15000", Instant.parse("2026-10-08T19:00:00Z"))), 24, "16500", doc(8, 24, WorkflowStatus.APPROVED));

        assertThat(service.between(ORG, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9))).hasSize(1);
        assertThat(service.between(ORG, LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 8))).isEmpty();
    }

    @Test
    void aClaimClosedOutsideTheWindow_isLeftAlone() {
        given(List.of(close(24, "15000", SEP_16_NOON)), 24, "16500", doc(8, 24, WorkflowStatus.APPROVED));

        assertThat(service.between(ORG, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 9))).isEmpty();
    }

    @Test
    void withNoApprovedReceipt_nothingWasCountedSoNothingIsCorrected() {
        given(List.of(close(24, "15000", SEP_16_NOON)), 24, "0", doc(8, 24, WorkflowStatus.VERIFIED));

        assertThat(service.between(ORG, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).isEmpty();
    }

    @Test
    void rupeesAreGroupedTheIndianWay() {
        assertThat(ClaimAdjustmentService.inr(new BigDecimal("886"))).isEqualTo("₹886");
        assertThat(ClaimAdjustmentService.inr(new BigDecimal("86576"))).isEqualTo("₹86,576");
        assertThat(ClaimAdjustmentService.inr(new BigDecimal("887376"))).isEqualTo("₹8,87,376");
        assertThat(ClaimAdjustmentService.inr(new BigDecimal("12345678"))).isEqualTo("₹1,23,45,678");
        assertThat(ClaimAdjustmentService.inr(new BigDecimal("-1500"))).isEqualTo("−₹1,500");
    }
}
