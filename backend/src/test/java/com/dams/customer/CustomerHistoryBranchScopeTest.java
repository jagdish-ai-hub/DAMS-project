package com.dams.customer;

import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.dto.CustomerHistoryResponse;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.customer.service.CustomerService;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.branch.repository.BranchRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receive.service.ReceivePaymentGuard;
import com.dams.vehicle.repository.VehicleRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Decision #2: a branch-restricted caller sees only their branches' job cards in a
 * customer's history — identity (name, phone, vehicles) stays visible, everything
 * derived from job cards (cards, timeline, totals) is scoped.
 */
@ExtendWith(MockitoExtension.class)
class CustomerHistoryBranchScopeTest {

    private static final long ORG = 1L;

    @Mock private CustomerRepository customerRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private ReceiveCategoryRepository categoryRepo;
    @Mock private ReceiveBusinessStatusRepository statusRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private SettlementModeRepository settlementModeRepo;
    @Mock private PendingAmountCalculator pendingAmountCalculator;
    @Mock private ReceivePaymentGuard paymentGuard;
    @Mock private BranchScope branchScope;
    @Mock private com.dams.jobcard.repository.ClaimCloseRepository claimCloseRepo;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepo, vehicleRepo, jobCardRepo, branchRepo,
            categoryRepo, statusRepo, receiveDocumentRepo, settlementLineRepo, settlementModeRepo,
            pendingAmountCalculator, paymentGuard, branchScope, claimCloseRepo);
        TenantContext.setOrgId(ORG);
        lenient().when(pendingAmountCalculator.forJobCard(any(JobCard.class)))
            .thenReturn(BigDecimal.ZERO);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void history_showsOnlyOwnBranchCards_identityStaysVisible() {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 1L);
        customer.setOrgId(ORG);
        customer.setName("Acme Transport");
        customer.setPhone("9999999999");
        when(customerRepo.findByIdAndOrgId(1L, ORG)).thenReturn(Optional.of(customer));
        when(jobCardRepo.findByOrgIdAndCustomerIdOrderByCreatedAtDesc(ORG, 1L))
            .thenReturn(List.of(jobCard(21L, 10L), jobCard(22L, 99L)));
        lenient().when(branchScope.allowedBranchIds()).thenReturn(Optional.of(Set.of(10L)));

        CustomerHistoryResponse history = service.history(1L);

        assertThat(history.jobCards()).extracting(CustomerHistoryResponse.JobCardSummary::id)
            .containsExactly(21L);
        assertThat(history.jobCardCount()).isEqualTo(1);
        assertThat(history.customerName()).isEqualTo("Acme Transport");
        assertThat(history.phone()).isEqualTo("9999999999");
    }

    private static JobCard jobCard(long id, long branchId) {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", id);
        jc.setOrgId(ORG);
        jc.setBranchId(branchId);
        jc.setCustomerId(1L);
        jc.setCategoryId(5L);
        jc.setBusinessStatusId(6L);
        jc.setInvoiceAmount(new BigDecimal("100"));
        return jc;
    }
}
