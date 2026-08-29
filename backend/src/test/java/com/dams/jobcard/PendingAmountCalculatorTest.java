package com.dams.jobcard;

import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.receive.repository.SettlementLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pending Amount is money logic — its behaviour is pinned here (AGENT.md "Tests are
 * required specifically for: pending-amount calculation ...").
 */
@ExtendWith(MockitoExtension.class)
class PendingAmountCalculatorTest {

    private static final long ORG = 1L;
    private static final long JOB_CARD = 50L;

    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ClaimCloseRepository claimCloseRepo;

    private PendingAmountCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PendingAmountCalculator(settlementLineRepo, claimCloseRepo);
        lenient().when(claimCloseRepo.existsByOrgIdAndJobCardId(eq(ORG), eq(JOB_CARD))).thenReturn(false);
        lenient().when(settlementLineRepo.sumAmountForJobCard(eq(ORG), eq(JOB_CARD))).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void pendingAmount_returnsZero_whenNoInvoiceYet() {
        assertThat(calculator.forJobCard(jobCard(null))).isZero();
    }

    @Test
    void pendingAmount_isInvoiceMinusSettledLines() {
        when(settlementLineRepo.sumAmountForJobCard(ORG, JOB_CARD)).thenReturn(new BigDecimal("12298"));
        assertThat(calculator.forJobCard(jobCard(new BigDecimal("15431"))))
            .isEqualByComparingTo("3133");
    }

    @Test
    void pendingAmount_returnsZero_whenFullyPaid() {
        when(settlementLineRepo.sumAmountForJobCard(ORG, JOB_CARD)).thenReturn(new BigDecimal("8600"));
        assertThat(calculator.forJobCard(jobCard(new BigDecimal("8600")))).isZero();
    }

    @Test
    void pendingAmount_neverGoesNegative_onOverpayment() {
        when(settlementLineRepo.sumAmountForJobCard(ORG, JOB_CARD)).thenReturn(new BigDecimal("100"));
        assertThat(calculator.forJobCard(jobCard(new BigDecimal("80")))).isZero();
    }

    @Test
    void pendingAmount_returnsZero_whenClaimClosed_evenWithShortfall() {
        // Kalaivani: invoiced 23,101, claim settled at 22,875 — the 226 shortfall must NOT
        // surface as a balance.
        when(claimCloseRepo.existsByOrgIdAndJobCardId(ORG, JOB_CARD)).thenReturn(true);
        // No sumAmountForJobCard stub: the claim-close check short-circuits before any summing.
        assertThat(calculator.forJobCard(jobCard(new BigDecimal("23101")))).isZero();
    }

    private static JobCard jobCard(BigDecimal invoiceAmount) {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", JOB_CARD);
        jc.setOrgId(ORG);
        jc.setInvoiceAmount(invoiceAmount);
        return jc;
    }
}
