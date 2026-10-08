package com.dams.customer;

import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.dto.CustomerHistoryResponse;
import com.dams.customer.dto.CustomerResponse;
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
import static org.mockito.ArgumentMatchers.eq;
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
    void expenses_includesCustomerLinkedExpensesWithNoJobCard_inScopeOnly_withoutDuplicates() {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", 1L);
        customer.setOrgId(ORG);
        customer.setName("Acme Transport");
        when(customerRepo.findByIdAndOrgId(1L, ORG)).thenReturn(Optional.of(customer));
        when(jobCardRepo.findByOrgIdAndCustomerIdOrderByCreatedAtDesc(ORG, 1L))
            .thenReturn(List.of(jobCard(21L, 10L)));
        lenient().when(branchScope.allowedBranchIds()).thenReturn(Optional.of(Set.of(10L)));

        ExpenseDocument tagged = expense(600L, 10L, 21L);     // on the customer's job card
        ExpenseDocument direct = expense(601L, 10L, null);    // linked to the customer, no job card
        ExpenseDocument otherBranch = expense(602L, 99L, null); // customer-linked but outside the caller's branches
        when(expenseDocumentRepo.findByOrgIdAndJobCardIdInOrderByCreatedAtDesc(ORG, List.of(21L)))
            .thenReturn(List.of(tagged));
        // the job-card-tagged document is also customer-linked (V34 backfill) — it must not repeat
        when(expenseDocumentRepo.findByOrgIdAndCustomerIdOrderByCreatedAtDesc(ORG, 1L))
            .thenReturn(List.of(tagged, direct, otherBranch));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(any(), any()))
            .thenReturn(List.of());

        List<CustomerExpenseEntry> entries = service.expenses(1L);

        assertThat(entries).extracting(CustomerExpenseEntry::id).containsExactlyInAnyOrder(600L, 601L);
        CustomerExpenseEntry noJobCard = entries.stream().filter(e -> e.id() == 601L).findFirst().orElseThrow();
        assertThat(noJobCard.jobCardId()).isNull();
        assertThat(noJobCard.jobCardReference()).isNull();
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

    private static ExpenseDocument expense(long id, long branchId, Long jobCardId) {
        ExpenseDocument d = new ExpenseDocument();
        ReflectionTestUtils.setField(d, "id", id);
        d.setOrgId(ORG);
        d.setBranchId(branchId);
        d.setJobCardId(jobCardId);
        d.setCustomerId(1L);
        d.setReceiverId(5L);
        d.setExpenseCategoryId(3L);
        d.setBusinessStatusId(1L);
        d.setWorkflowStatus(ExpenseWorkflowStatus.SUBMITTED);
        return d;
    }

    /** rev 68: the picker pre-fills Contact from each customer's most recently saved receipt number. */
    @Test
    void search_returnsEachCustomersNewestSavedContact_andNullWhenNoneWasEverSaved() {
        Customer a = customer(1L, "Acme Transport");
        Customer b = customer(2L, "Beta Motors");
        Customer c = customer(3L, "Cee Traders");
        when(branchScope.allowedBranchIds()).thenReturn(Optional.empty());   // owner / FM: unrestricted
        when(customerRepo.search(eq(ORG), eq("a"), any())).thenReturn(List.of(a, b, c));
        // newest first, as the query orders them: customer 1 has two numbers saved, 3 has none
        when(jobCardRepo.findContactPhonesNewestFirst(ORG, List.of(1L, 2L, 3L))).thenReturn(List.of(
            new Object[] {1L, "98765 43210"},
            new Object[] {2L, "88888 00000"},
            new Object[] {1L, "90000 11111"}));

        List<CustomerResponse> found = service.search("a");

        assertThat(found).extracting(CustomerResponse::lastContactPhone)
            .containsExactly("98765 43210", "88888 00000", null);
    }

    private static Customer customer(long id, String name) {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", id);
        c.setOrgId(ORG);
        c.setName(name);
        return c;
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
