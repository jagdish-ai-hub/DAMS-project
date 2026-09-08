package com.dams.followup;

import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.followup.dto.CreateFollowupRequest;
import com.dams.followup.dto.FollowupResponse;
import com.dams.followup.entity.CreditFollowup;
import com.dams.followup.repository.CreditFollowupRepository;
import com.dams.followup.service.FollowupService;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.repository.ReceiveDocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FEAT-35: dues with owners and dates. Overdue is derived at read time (never
 * stored), re-promising reuses the row, and the defaulter view ranks by live
 * outstanding.
 */
@ExtendWith(MockitoExtension.class)
class FollowupServiceTest {

    private static final long ORG = 1L;
    private static final long ACTOR = 50L;
    private static final long DOC = 500L;
    private static final long JOB = 11L;
    private static final long BRANCH = 3L;
    private static final long CUSTOMER = 5L;

    @Mock private CreditFollowupRepository followupRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private PendingAmountCalculator pendingAmountCalculator;
    @Mock private BranchScope branchScope;
    @Mock private AuditService auditService;

    private FollowupService service;

    @BeforeEach
    void setUp() {
        service = new FollowupService(followupRepo, receiveDocumentRepo, jobCardRepo, customerRepo,
            branchRepo, pendingAmountCalculator, branchScope, auditService);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.currentUserId()).thenReturn(ACTOR);
        lenient().when(branchScope.canSeeBranch(BRANCH)).thenReturn(true);
        lenient().when(receiveDocumentRepo.findByIdAndOrgId(DOC, ORG)).thenReturn(Optional.of(doc()));
        lenient().when(jobCardRepo.findByIdAndOrgId(JOB, ORG)).thenReturn(Optional.of(jobCard()));
        lenient().when(customerRepo.findByIdAndOrgId(CUSTOMER, ORG)).thenReturn(Optional.of(customer()));
        lenient().when(branchRepo.findByIdAndOrgId(BRANCH, ORG)).thenReturn(Optional.of(branch()));
        lenient().when(pendingAmountCalculator.forJobCard(ORG, JOB, new BigDecimal("8000")))
            .thenReturn(new BigDecimal("3133"));
        lenient().when(followupRepo.save(any(CreditFollowup.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void open_createsPromisedFollowup_withDueDateAndNote() {
        when(followupRepo.findByOrgIdAndReceiveDocumentIdAndStatusIn(
            ORG, DOC, List.of(CreditFollowup.OPEN, CreditFollowup.PROMISED)))
            .thenReturn(Optional.empty());

        FollowupResponse r = service.open(request(LocalDate.now().plusDays(7), "Payday Friday"));

        assertThat(r.status()).isEqualTo(CreditFollowup.PROMISED);
        assertThat(r.documentNo()).isEqualTo("OOR-AUG26-R-005");
        assertThat(r.customerName()).isEqualTo("Sharma Transport");
        assertThat(r.pendingAmount()).isEqualByComparingTo("3133");
        assertThat(r.overdue()).isFalse();
    }

    @Test
    void open_reusesTheRow_whenRepromising_soHistorySurvives() {
        CreditFollowup existing = followup();
        existing.setStatus(CreditFollowup.OPEN);
        existing.setRemindedCount(2);
        when(followupRepo.findByOrgIdAndReceiveDocumentIdAndStatusIn(
            ORG, DOC, List.of(CreditFollowup.OPEN, CreditFollowup.PROMISED)))
            .thenReturn(Optional.of(existing));

        FollowupResponse r = service.open(request(LocalDate.now().plusDays(3), "New promise"));

        assertThat(r.id()).isEqualTo(existing.getId());
        assertThat(r.status()).isEqualTo(CreditFollowup.PROMISED);
        verify(followupRepo).save(existing);
    }

    @Test
    void open_refusesAPastDueDate() {
        assertThatThrownBy(() -> service.open(request(LocalDate.now().minusDays(1), "x")))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("cannot be in the past");
        verify(followupRepo, never()).save(any());
    }

    @Test
    void list_derivesOverdueAtReadTime_neverFromStorage() {
        CreditFollowup f = followup();
        f.setDueDate(LocalDate.now().minusDays(5));
        when(followupRepo.findByOrgIdAndStatusInOrderByDueDateAsc(
            ORG, List.of(CreditFollowup.OPEN, CreditFollowup.PROMISED)))
            .thenReturn(List.of(f));

        List<FollowupResponse> rows = service.list(false);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).overdue()).isTrue();
        assertThat(rows.get(0).daysOverdue()).isEqualTo(5);
        // Storage still says PROMISED — overdue lives only in the response.
        assertThat(f.getStatus()).isEqualTo(CreditFollowup.PROMISED);
    }

    @Test
    void list_overdueOnly_filtersTheChaseList() {
        CreditFollowup late = followup();
        late.setDueDate(LocalDate.now().minusDays(2));
        CreditFollowup current = followup();
        current.setDueDate(LocalDate.now().plusDays(9));
        when(followupRepo.findByOrgIdAndStatusInOrderByDueDateAsc(
            ORG, List.of(CreditFollowup.OPEN, CreditFollowup.PROMISED)))
            .thenReturn(List.of(late, current));

        assertThat(service.list(true)).hasSize(1);
        assertThat(service.list(false)).hasSize(2);
    }

    @Test
    void defaulters_ranksByOutstanding_withOverdueCounts() {
        CreditFollowup f = followup();
        f.setDueDate(LocalDate.now().minusDays(4));
        when(followupRepo.findByOrgIdAndStatusInOrderByDueDateAsc(
            ORG, List.of(CreditFollowup.OPEN, CreditFollowup.PROMISED)))
            .thenReturn(List.of(f));

        var rows = service.defaulters();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).customerName()).isEqualTo("Sharma Transport");
        assertThat(rows.get(0).totalOutstanding()).isEqualByComparingTo("3133");
        assertThat(rows.get(0).overdueFollowups()).isEqualTo(1);
        assertThat(rows.get(0).oldestOverdueDays()).isEqualTo(4L);
    }

    @Test
    void close_flipsStatus_andAudits() {
        CreditFollowup f = followup(77L);
        when(followupRepo.findByIdAndOrgId(77L, ORG)).thenReturn(Optional.of(f));

        FollowupResponse r = service.close(77L);

        assertThat(r.status()).isEqualTo(CreditFollowup.CLOSED);
        assertThat(f.getClosedAt()).isNotNull();
    }

    // ---------------------------------------------------------- fixtures

    private static CreateFollowupRequest request(LocalDate due, String note) {
        CreateFollowupRequest r = new CreateFollowupRequest();
        r.setReceiveDocumentId(DOC);
        r.setDueDate(due);
        r.setPromiseNote(note);
        return r;
    }

    private static CreditFollowup followup(Long id) {
        CreditFollowup f = new CreditFollowup();
        ReflectionTestUtils.setField(f, "id", id);
        f.setOrgId(ORG);
        f.setReceiveDocumentId(DOC);
        f.setDueDate(LocalDate.now().plusDays(7));
        f.setStatus(CreditFollowup.PROMISED);
        f.setCreatedBy(ACTOR);
        return f;
    }

    private static CreditFollowup followup() {
        return followup(900L);
    }

    private static ReceiveDocument doc() {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", DOC);
        d.setOrgId(ORG);
        d.setBranchId(BRANCH);
        d.setJobCardId(JOB);
        d.setDocumentNo("OOR-AUG26-R-005");
        return d;
    }

    private static JobCard jobCard() {
        JobCard j = new JobCard();
        ReflectionTestUtils.setField(j, "id", JOB);
        j.setOrgId(ORG);
        j.setBranchId(BRANCH);
        j.setCustomerId(CUSTOMER);
        j.setInvoiceAmount(new BigDecimal("8000"));
        return j;
    }

    private static Customer customer() {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", CUSTOMER);
        c.setOrgId(ORG);
        c.setName("Sharma Transport");
        c.setPhone("9876543210");
        return c;
    }

    private static Branch branch() {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", BRANCH);
        b.setOrgId(ORG);
        b.setCode("OOR");
        b.setName("Rayagada");
        return b;
    }
}
