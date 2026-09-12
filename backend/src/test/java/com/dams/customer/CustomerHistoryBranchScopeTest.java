package com.dams.customer;

import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.dto.CustomerHistoryResponse;
import com.dams.customer.entity.Customer;
import com.dams.customer.dto.CustomerExpenseEntry;
import com.dams.customer.repository.CustomerRepository;
import com.dams.customer.service.CustomerService;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.branch.repository.BranchRepository;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receive.service.ReceivePaymentGuard;
import com.dams.receiver.repository.ReceiverRepository;
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
    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private ExpenseCategoryRepository expenseCategoryRepo;
    @Mock private ReceiverRepository receiverRepo;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepo, vehicleRepo, jobCardRepo, branchRepo,
            categoryRepo, statusRepo, receiveDocumentRepo, settlementLineRepo, settlementModeRepo,
            pendingAmountCalculator, paymentGuard, branchScope,
            expenseDocumentRepo, expenseLineRepo, expenseCategoryRepo, receiverRepo);
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

    @Test
    void expenses_showsOnlyOwnBranchJobCards_withTheLineSumAsAmount() {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 1L);
        customer.setOrgId(ORG);
        customer.setName("Acme Transport");
        when(customerRepo.findByIdAndOrgId(1L, ORG)).thenReturn(Optional.of(customer));
        when(jobCardRepo.findByOrgIdAndCustomerIdOrderByCreatedAtDesc(ORG, 1L))
            .thenReturn(List.of(jobCard(21L, 10L), jobCard(22L, 99L)));
        lenient().when(branchScope.allowedBranchIds()).thenReturn(Optional.of(Set.of(10L)));

        ExpenseDocument doc = new ExpenseDocument();
        ReflectionTestUtils.setField(doc, "id", 600L);
        doc.setOrgId(ORG);
        doc.setBranchId(10L);
        doc.setJobCardId(21L);
        doc.setReceiverId(5L);
        doc.setExpenseCategoryId(3L);
        doc.setBusinessStatusId(1L);
        doc.setDocumentNo("OOK-SEP26-E-001");
        doc.setWorkflowStatus(ExpenseWorkflowStatus.APPROVED);
        when(expenseDocumentRepo.findByOrgIdAndJobCardIdInOrderByCreatedAtDesc(ORG, List.of(21L)))
            .thenReturn(List.of(doc));

        ExpenseLine l1 = new ExpenseLine();
        l1.setOrgId(ORG);
        l1.setExpenseDocumentId(600L);
        l1.setLineNo(1);
        l1.setAmount(new BigDecimal("400"));
        ExpenseLine l2 = new ExpenseLine();
        l2.setOrgId(ORG);
        l2.setExpenseDocumentId(600L);
        l2.setLineNo(2);
        l2.setAmount(new BigDecimal("150"));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(ORG, List.of(600L)))
            .thenReturn(List.of(l1, l2));

        List<CustomerExpenseEntry> entries = service.expenses(1L);

        assertThat(entries).hasSize(1);
        CustomerExpenseEntry entry = entries.get(0);
        assertThat(entry.jobCardId()).isEqualTo(21L);
        assertThat(entry.amount()).isEqualByComparingTo(new BigDecimal("550"));
        assertThat(entry.workflowStatus()).isEqualTo("APPROVED");
    }

    @Test
    void expenses_returnsEmpty_whenTheCustomerHasNoJobCardsInScope() {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 1L);
        customer.setOrgId(ORG);
        customer.setName("Acme Transport");
        when(customerRepo.findByIdAndOrgId(1L, ORG)).thenReturn(Optional.of(customer));
        when(jobCardRepo.findByOrgIdAndCustomerIdOrderByCreatedAtDesc(ORG, 1L))
            .thenReturn(List.of(jobCard(22L, 99L)));
        lenient().when(branchScope.allowedBranchIds()).thenReturn(Optional.of(Set.of(10L)));

        assertThat(service.expenses(1L)).isEmpty();
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
