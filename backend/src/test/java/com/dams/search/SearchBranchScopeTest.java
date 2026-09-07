package com.dams.search;

import com.dams.branch.repository.BranchRepository;
import com.dams.config.TenantContext;
import com.dams.common.security.BranchScope;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.search.dto.SearchResponse;
import com.dams.search.service.SearchService;
import com.dams.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * Decision #2: identity may match org-wide, but a branch-restricted caller only sees
 * customers with job cards in their branches — a name hit elsewhere stays hidden.
 */
@ExtendWith(MockitoExtension.class)
class SearchBranchScopeTest {

    private static final long ORG = 1L;

    @Mock private CustomerRepository customerRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private BranchScope branchScope;
    @Mock private PendingAmountCalculator pendingAmountCalculator;
    @Mock private BranchRepository branchRepo;

    private SearchService service;

    @BeforeEach
    void setUp() {
        service = new SearchService(customerRepo, vehicleRepo, jobCardRepo,
            receiveDocumentRepo, expenseDocumentRepo, settlementLineRepo, expenseLineRepo,
            branchScope, pendingAmountCalculator,
            branchRepo);
        TenantContext.setOrgId(ORG);
        lenient().when(pendingAmountCalculator.forJobCard(any(JobCard.class)))
            .thenReturn(BigDecimal.ZERO);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void restrictedCaller_doesNotSeeCustomerWithOnlyOutOfBranchCards() {
        lenient().when(branchScope.allowedBranchIds()).thenReturn(Optional.of(Set.of(10L)));
        seedNameHit(99L);

        SearchResponse response = service.search("Acme");

        assertThat(response.hits()).isEmpty();
    }

    @Test
    void unrestrictedCaller_seesTheSameCustomer() {
        lenient().when(branchScope.allowedBranchIds()).thenReturn(Optional.empty());
        seedNameHit(99L);

        SearchResponse response = service.search("Acme");

        assertThat(response.hits()).hasSize(1);
        assertThat(response.hits().get(0).matchField()).isEqualTo("Name");
    }

    @Test
    void utrFragment_resolvesThroughParentDocumentToTheJobCardCustomer() {
        lenient().when(branchScope.allowedBranchIds()).thenReturn(Optional.empty());
        seedNameHit(10L);
        // The name/phone index must stay quiet for this query — only the UTR may match.
        lenient().when(customerRepo.search(eq(ORG), eq("9876"), any(Limit.class)))
            .thenReturn(List.of());
        com.dams.receive.entity.SettlementLine line = new com.dams.receive.entity.SettlementLine();
        org.springframework.test.util.ReflectionTestUtils.setField(line, "id", 300L);
        line.setOrgId(ORG);
        line.setReceiveDocumentId(50L);
        line.setTransactionRef("UTR987654321");
        com.dams.receive.entity.ReceiveDocument doc = new com.dams.receive.entity.ReceiveDocument();
        org.springframework.test.util.ReflectionTestUtils.setField(doc, "id", 50L);
        doc.setOrgId(ORG);
        doc.setBranchId(10L);
        doc.setJobCardId(21L);
        lenient().when(settlementLineRepo.findByOrgIdAndTransactionRefContainingIgnoreCase(
                eq(ORG), eq("9876"), any(org.springframework.data.domain.Limit.class)))
            .thenReturn(List.of(line));
        lenient().when(receiveDocumentRepo.findByIdAndOrgId(50L, ORG)).thenReturn(Optional.of(doc));
        JobCard card = new JobCard();
        ReflectionTestUtils.setField(card, "id", 21L);
        card.setOrgId(ORG);
        card.setBranchId(10L);
        card.setCustomerId(5L);
        card.setCategoryId(1L);
        card.setBusinessStatusId(1L);
        card.setInvoiceAmount(new BigDecimal("100"));
        lenient().when(jobCardRepo.findByIdAndOrgId(21L, ORG)).thenReturn(Optional.of(card));

        SearchResponse response = service.search("9876");

        assertThat(response.hits()).hasSize(1);
        assertThat(response.hits().get(0).matchField()).isEqualTo("UTR");
    }

    @Test
    void shortFragment_neverTouchesTheUtrIndex() {
        lenient().when(branchScope.allowedBranchIds()).thenReturn(Optional.empty());

        service.search("98");

        org.mockito.Mockito.verify(settlementLineRepo, org.mockito.Mockito.never())
            .findByOrgIdAndTransactionRefContainingIgnoreCase(any(), anyString(), any(Limit.class));
        org.mockito.Mockito.verify(expenseLineRepo, org.mockito.Mockito.never())
            .findByOrgIdAndTransactionRefContainingIgnoreCase(any(), anyString(), any(Limit.class));
    }

    private void seedNameHit(long cardBranchId) {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 5L);
        customer.setOrgId(ORG);
        customer.setName("Acme Transport");
        lenient().when(customerRepo.search(eq(ORG), anyString(), any(Limit.class)))
            .thenReturn(List.of(customer));
        lenient().when(customerRepo.findByOrgIdAndIdInOrderByNameAsc(eq(ORG), any()))
            .thenReturn(List.of(customer));
        JobCard card = new JobCard();
        ReflectionTestUtils.setField(card, "id", 21L);
        card.setOrgId(ORG);
        card.setBranchId(cardBranchId);
        card.setCustomerId(5L);
        card.setCategoryId(1L);
        card.setBusinessStatusId(1L);
        card.setInvoiceAmount(new BigDecimal("100"));
        lenient().when(jobCardRepo.findByOrgIdAndCustomerIdInOrderByCreatedAtDesc(eq(ORG), any()))
            .thenReturn(List.of(card));
    }
}
