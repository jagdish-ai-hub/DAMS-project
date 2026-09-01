package com.dams.expense;

import com.dams.attachment.repository.AttachmentRepository;
import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.entity.DocType;
import com.dams.branch.repository.BranchRepository;
import com.dams.branch.service.DocumentNumberService;
import com.dams.cash.service.CashDateLock;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.dto.CreateExpenseRequest;
import com.dams.expense.dto.ExpenseLineInput;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.expense.service.ExpenseDocumentService;
import com.dams.expense.service.ExpensePostingGuard;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.masters.entity.ExpenseBusinessStatus;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.ExpenseSubCategory;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.repository.BankRepository;
import com.dams.masters.repository.ExpenseBusinessStatusRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.ExpenseSubCategoryRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Expense-document rules for Stage 5: inline receiver dedup, {@code over_limit} recompute,
 * gap-free {@code E} numbering + line-id stamping on submit, no lines once a document is
 * closed, and "Transfer to Claim" allowed only on a claim job card.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseDocumentServiceTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
    private static final long HOME_BRANCH = 3L;
    private static final long DOC_ID = 600L;

    private static final long CATEGORY_ID = 20L;
    private static final long STATUS_ID = 30L;         // ordinary status
    private static final long CLAIM_STATUS_ID = 31L;   // triggers_claim = true
    private static final long SUBCAT_ID = 40L;         // limit 1000, under CATEGORY_ID
    private static final long MODE_ID = 50L;           // Cash — no flags
    private static final long RECEIVER_ID = 60L;
    private static final long JC_CLAIM = 70L;          // category is_claim = true
    private static final long JC_PLAIN = 71L;          // category is_claim = false
    private static final long RC_CLAIM = 80L;
    private static final long RC_PLAIN = 81L;

    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private ReceiverRepository receiverRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private ExpenseCategoryRepository expenseCategoryRepo;
    @Mock private ExpenseSubCategoryRepository subCategoryRepo;
    @Mock private ExpenseModeRepository expenseModeRepo;
    @Mock private ExpenseBusinessStatusRepository statusRepo;
    @Mock private ReceiveCategoryRepository receiveCategoryRepo;
    @Mock private BankRepository bankRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private AttachmentRepository attachmentRepo;
    @Mock private DocumentNumberService documentNumberService;
    @Mock private ExpensePostingGuard postingGuard;
    @Mock private CashDateLock cashDateLock;
    @Mock private AuditService auditService;
    @Mock private com.dams.audit.service.DocumentHistoryService documentHistoryService;
    @Mock private com.dams.common.security.BranchScope branchScope;

    private ExpenseDocumentService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseDocumentService(expenseDocumentRepo, expenseLineRepo, receiverRepo, jobCardRepo,
            customerRepo, vehicleRepo, branchRepo, expenseCategoryRepo, subCategoryRepo, expenseModeRepo,
            statusRepo, receiveCategoryRepo, bankRepo, userRepo, attachmentRepo, documentNumberService,
            postingGuard, cashDateLock, auditService, documentHistoryService, branchScope);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.canSeeBranch(anyLong())).thenReturn(true);

        lenient().when(postingGuard.requireCanPost(eq(ORG), any())).thenReturn(cashier());
        lenient().when(receiverRepo.findByIdAndOrgId(RECEIVER_ID, ORG)).thenReturn(Optional.of(receiver()));
        lenient().when(receiverRepo.findFirstByOrgIdAndNameIgnoreCase(eq(ORG), any()))
            .thenReturn(Optional.of(receiver()));
        lenient().when(expenseCategoryRepo.findByIdAndOrgId(CATEGORY_ID, ORG)).thenReturn(Optional.of(category()));
        lenient().when(statusRepo.findByIdAndOrgId(STATUS_ID, ORG)).thenReturn(Optional.of(status(STATUS_ID, false)));
        lenient().when(statusRepo.findByIdAndOrgId(CLAIM_STATUS_ID, ORG))
            .thenReturn(Optional.of(status(CLAIM_STATUS_ID, true)));
        lenient().when(subCategoryRepo.findByIdAndOrgId(SUBCAT_ID, ORG)).thenReturn(Optional.of(sub()));
        lenient().when(subCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of(sub()));
        lenient().when(expenseModeRepo.findByIdAndOrgId(MODE_ID, ORG)).thenReturn(Optional.of(mode()));
        lenient().when(expenseModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of(mode()));
        lenient().when(bankRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of());
        lenient().when(branchRepo.findByIdAndOrgId(HOME_BRANCH, ORG)).thenReturn(Optional.of(branch()));
        lenient().when(userRepo.findById(CASHIER_ID)).thenReturn(Optional.of(cashier()));
        lenient().when(attachmentRepo.countByOrgIdAndParentTypeAndParentId(any(), any(), any())).thenReturn(0L);
        lenient().when(expenseLineRepo.maxLineNo(anyLong())).thenReturn(0);
        lenient().when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(eq(ORG), anyLong()))
            .thenReturn(List.of());
        lenient().when(expenseLineRepo.save(any(ExpenseLine.class))).thenAnswer(inv -> {
            ExpenseLine l = inv.getArgument(0);
            if (l.getId() == null) ReflectionTestUtils.setField(l, "id", 900L + l.getLineNo());
            return l;
        });
        lenient().when(expenseDocumentRepo.save(any(ExpenseDocument.class))).thenAnswer(inv -> {
            ExpenseDocument d = inv.getArgument(0);
            if (d.getId() == null) ReflectionTestUtils.setField(d, "id", DOC_ID);
            return d;
        });
        lenient().when(jobCardRepo.findByIdAndOrgId(JC_CLAIM, ORG)).thenReturn(Optional.of(jobCard(JC_CLAIM, RC_CLAIM)));
        lenient().when(jobCardRepo.findByIdAndOrgId(JC_PLAIN, ORG)).thenReturn(Optional.of(jobCard(JC_PLAIN, RC_PLAIN)));
        lenient().when(receiveCategoryRepo.findByIdAndOrgId(RC_CLAIM, ORG)).thenReturn(Optional.of(receiveCat(true)));
        lenient().when(receiveCategoryRepo.findByIdAndOrgId(RC_PLAIN, ORG)).thenReturn(Optional.of(receiveCat(false)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_reusesAnExistingReceiver_matchedByName() {
        CreateExpenseRequest req = baseRequest();
        req.setReceiverName("Sharma Auto Spares");
        req.getLines().add(lineInput(new BigDecimal("500")));

        service.create(req);

        verify(receiverRepo, never()).save(any());
        ArgumentCaptor<ExpenseDocument> doc = ArgumentCaptor.forClass(ExpenseDocument.class);
        verify(expenseDocumentRepo, atLeastOnce()).save(doc.capture());
        assertThat(doc.getValue().getReceiverId()).isEqualTo(RECEIVER_ID);
        assertThat(doc.getValue().getBranchId()).isEqualTo(HOME_BRANCH);
    }

    @Test
    void create_flagsOverLimit_whenALineExceedsItsSubCategoryLimit() {
        CreateExpenseRequest req = baseRequest();
        req.setReceiverId(RECEIVER_ID);
        req.getLines().add(lineInput(new BigDecimal("1400")));   // limit is 1000
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(ORG, DOC_ID))
            .thenReturn(List.of(persistedLine(1, new BigDecimal("1400"))));

        service.create(req);

        ArgumentCaptor<ExpenseDocument> doc = ArgumentCaptor.forClass(ExpenseDocument.class);
        verify(expenseDocumentRepo, atLeastOnce()).save(doc.capture());
        assertThat(doc.getValue().isOverLimit()).isTrue();
    }

    @Test
    void submit_assignsAGapFreeExpenseNumber_stampsLineIds_andAuditsSubmitted() {
        ExpenseDocument draft = draftDoc();
        when(expenseDocumentRepo.findByIdAndOrgId(DOC_ID, ORG)).thenReturn(Optional.of(draft));
        ExpenseLine l1 = persistedLine(1, new BigDecimal("500"));
        when(expenseLineRepo.findByOrgIdAndExpenseDocumentIdOrderByLineNoAsc(ORG, DOC_ID)).thenReturn(List.of(l1));
        when(documentNumberService.nextNumber(eq(ORG), any(Branch.class), eq(DocType.E)))
            .thenReturn("OOR-AUG26-E-001");

        service.submit(DOC_ID);

        assertThat(draft.getDocumentNo()).isEqualTo("OOR-AUG26-E-001");
        assertThat(draft.getWorkflowStatus()).isEqualTo(ExpenseWorkflowStatus.SUBMITTED);
        assertThat(l1.getLineId()).isEqualTo("OOR-AUG26-E-001-L1");
        verify(auditService).recordUserEvent(eq("ExpenseDocument"), eq(DOC_ID), any(), eq(EventType.SUBMITTED),
            eq(CASHIER_ID), any());
    }

    @Test
    void addLine_isRejected_onAClosedDocument() {
        ExpenseDocument closed = draftDoc();
        closed.setWorkflowStatus(ExpenseWorkflowStatus.CLOSED);
        when(expenseDocumentRepo.findByIdAndOrgId(DOC_ID, ORG)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.addLine(DOC_ID, lineInput(new BigDecimal("100"))))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("no more expense lines");
        verify(expenseLineRepo, never()).save(any());
    }

    @Test
    void transferToClaim_isRejected_whenTheJobCardIsNotAClaimCategory() {
        ExpenseDocument doc = submittedDoc(JC_PLAIN);
        when(expenseDocumentRepo.findByIdAndOrgId(DOC_ID, ORG)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.transferToClaim(DOC_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("not a claim job card");
        verify(auditService, never()).recordUserEvent(any(), any(), any(), eq(EventType.TRANSFERRED_TO_CLAIM), any(), any());
    }

    @Test
    void transferToClaim_setsTheClaimStatus_andAudits_whenEligible() {
        ExpenseDocument doc = submittedDoc(JC_CLAIM);
        when(expenseDocumentRepo.findByIdAndOrgId(DOC_ID, ORG)).thenReturn(Optional.of(doc));
        when(statusRepo.findByOrgIdAndTriggersClaimTrue(ORG))
            .thenReturn(List.of(status(CLAIM_STATUS_ID, true)));

        service.transferToClaim(DOC_ID);

        assertThat(doc.getBusinessStatusId()).isEqualTo(CLAIM_STATUS_ID);
        verify(auditService).recordUserEvent(eq("ExpenseDocument"), eq(DOC_ID), any(),
            eq(EventType.TRANSFERRED_TO_CLAIM), eq(CASHIER_ID), any());
    }

    // --- fixtures ---

    private static CreateExpenseRequest baseRequest() {
        CreateExpenseRequest r = new CreateExpenseRequest();
        r.setExpenseCategoryId(CATEGORY_ID);
        r.setBusinessStatusId(STATUS_ID);
        return r;
    }

    private static ExpenseLineInput lineInput(BigDecimal amount) {
        ExpenseLineInput in = new ExpenseLineInput();
        in.setTransactionDate(LocalDate.of(2026, 8, 30));
        in.setSubCategoryId(SUBCAT_ID);
        in.setExpenseModeId(MODE_ID);
        in.setAmount(amount);
        return in;
    }

    private static ExpenseDocument draftDoc() {
        ExpenseDocument d = new ExpenseDocument();
        ReflectionTestUtils.setField(d, "id", DOC_ID);
        d.setOrgId(ORG);
        d.setBranchId(HOME_BRANCH);
        d.setReceiverId(RECEIVER_ID);
        d.setExpenseCategoryId(CATEGORY_ID);
        d.setBusinessStatusId(STATUS_ID);
        d.setWorkflowStatus(ExpenseWorkflowStatus.DRAFT);
        d.setCreatedBy(CASHIER_ID);
        return d;
    }

    private static ExpenseDocument submittedDoc(long jobCardId) {
        ExpenseDocument d = draftDoc();
        d.setJobCardId(jobCardId);
        d.setDocumentNo("OOR-JUL26-E-002");
        d.setWorkflowStatus(ExpenseWorkflowStatus.SUBMITTED);
        return d;
    }

    private static ExpenseLine persistedLine(int no, BigDecimal amount) {
        ExpenseLine l = new ExpenseLine();
        ReflectionTestUtils.setField(l, "id", 900L + no);
        l.setOrgId(ORG);
        l.setExpenseDocumentId(DOC_ID);
        l.setLineNo(no);
        l.setAmount(amount);
        l.setSubCategoryId(SUBCAT_ID);
        l.setExpenseModeId(MODE_ID);
        l.setTransactionDate(LocalDate.of(2026, 8, 30));
        return l;
    }

    private static AppUser cashier() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", CASHIER_ID);
        u.setName("Bikram Nayak");
        u.setRole(Role.CASHIER);
        u.setHomeBranchId(HOME_BRANCH);
        return u;
    }

    private static Receiver receiver() {
        Receiver r = new Receiver();
        ReflectionTestUtils.setField(r, "id", RECEIVER_ID);
        r.setOrgId(ORG);
        r.setName("Sharma Auto Spares");
        return r;
    }

    private static ExpenseCategory category() {
        ExpenseCategory c = new ExpenseCategory();
        ReflectionTestUtils.setField(c, "id", CATEGORY_ID);
        c.setOrgId(ORG);
        c.setName("Service");
        c.setActive(true);
        return c;
    }

    private static ExpenseBusinessStatus status(long id, boolean triggersClaim) {
        ExpenseBusinessStatus s = new ExpenseBusinessStatus();
        ReflectionTestUtils.setField(s, "id", id);
        s.setOrgId(ORG);
        s.setName(triggersClaim ? "Transfer to Claim" : "Awaiting Receipt");
        s.setActive(true);
        s.setTriggersClaim(triggersClaim);
        return s;
    }

    private static ExpenseSubCategory sub() {
        ExpenseSubCategory s = new ExpenseSubCategory();
        ReflectionTestUtils.setField(s, "id", SUBCAT_ID);
        s.setOrgId(ORG);
        s.setName("Spare Transport");
        s.setActive(true);
        s.setExpenseCategoryId(CATEGORY_ID);
        s.setLimitAmount(new BigDecimal("1000"));
        return s;
    }

    private static ExpenseMode mode() {
        ExpenseMode m = new ExpenseMode();
        ReflectionTestUtils.setField(m, "id", MODE_ID);
        m.setOrgId(ORG);
        m.setName("Cash");
        m.setActive(true);
        return m;
    }

    private static Branch branch() {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", HOME_BRANCH);
        b.setOrgId(ORG);
        b.setCode("OOR");
        b.setName("Rayagada");
        b.setActive(true);
        return b;
    }

    private static JobCard jobCard(long id, long categoryId) {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", id);
        jc.setOrgId(ORG);
        jc.setBranchId(HOME_BRANCH);
        jc.setCustomerId(42L);
        jc.setCategoryId(categoryId);
        jc.setBusinessStatusId(1L);
        return jc;
    }

    private static ReceiveCategory receiveCat(boolean claim) {
        ReceiveCategory c = new ReceiveCategory();
        ReflectionTestUtils.setField(c, "id", claim ? RC_CLAIM : RC_PLAIN);
        c.setOrgId(ORG);
        c.setName(claim ? "Warranty" : "Workshop");
        c.setClaim(claim);
        c.setActive(true);
        return c;
    }
}
