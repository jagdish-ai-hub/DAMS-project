package com.dams.receive;

import com.dams.attachment.repository.AttachmentRepository;
import com.dams.attachment.service.AttachmentService;
import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.entity.DocType;
import com.dams.branch.repository.BranchRepository;
import com.dams.branch.service.DocumentNumberService;
import com.dams.cash.service.CashDateLock;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.dto.JobCardCreateRequest;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.JobCardService;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.BankRepository;
import com.dams.masters.repository.ClaimTypeRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.dto.CreateReceiptRequest;
import com.dams.receive.dto.SettlementLineInput;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receive.service.ReceiveDocumentService;
import com.dams.receive.service.ReceivePaymentGuard;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Receive-document rules for Stage 4: Add Payment appends to the one open document (never a
 * second), a closed claim blocks a new receipt, submit assigns a gap-free number and stamps
 * line ids, and the document auto-settles with a SYSTEM event when Pending Amount hits 0.
 */
@ExtendWith(MockitoExtension.class)
class ReceiveDocumentServiceTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
    private static final long JOB_CARD_ID = 50L;
    private static final long BRANCH_ID = 3L;

    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private JobCardService jobCardService;
    @Mock private CustomerRepository customerRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private ReceiveCategoryRepository categoryRepo;
    @Mock private ReceiveBusinessStatusRepository statusRepo;
    @Mock private ClaimTypeRepository claimTypeRepo;
    @Mock private SettlementModeRepository settlementModeRepo;
    @Mock private BankRepository bankRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private ClaimCloseRepository claimCloseRepo;
    @Mock private AttachmentRepository attachmentRepo;
    @Mock private DocumentNumberService documentNumberService;
    @Mock private PendingAmountCalculator pendingAmountCalculator;
    @Mock private ReceivePaymentGuard paymentGuard;
    @Mock private CashDateLock cashDateLock;
    @Mock private AuditService auditService;
    @Mock private AttachmentService attachmentService;
    @Mock private com.dams.audit.service.DocumentHistoryService documentHistoryService;
    @Mock private com.dams.common.security.BranchScope branchScope;

    private ReceiveDocumentService service;

    @BeforeEach
    void setUp() {
        service = new ReceiveDocumentService(receiveDocumentRepo, settlementLineRepo, jobCardRepo,
            jobCardService, customerRepo, vehicleRepo, branchRepo, categoryRepo, statusRepo, claimTypeRepo,
            settlementModeRepo, bankRepo, userRepo, claimCloseRepo, attachmentRepo,
            documentNumberService, pendingAmountCalculator, paymentGuard, cashDateLock, auditService,
            attachmentService, documentHistoryService, branchScope);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.canSeeBranch(anyLong())).thenReturn(true);

        lenient().when(jobCardRepo.findByIdAndOrgId(JOB_CARD_ID, ORG)).thenReturn(Optional.of(jobCard(new BigDecimal("15431"))));
        lenient().when(paymentGuard.requireCanPost(eq(ORG), any(JobCard.class))).thenReturn(cashier());
        lenient().when(claimCloseRepo.existsByOrgIdAndJobCardId(ORG, JOB_CARD_ID)).thenReturn(false);
        lenient().when(claimCloseRepo.findByOrgIdAndJobCardId(ORG, JOB_CARD_ID)).thenReturn(Optional.empty());
        lenient().when(pendingAmountCalculator.forJobCard(any(JobCard.class))).thenReturn(new BigDecimal("3133"));
        lenient().when(pendingAmountCalculator.hasClaimClose(ORG, JOB_CARD_ID)).thenReturn(false);
        lenient().when(settlementLineRepo.maxLineNo(anyLong())).thenReturn(0);
        lenient().when(settlementLineRepo.save(any(SettlementLine.class))).thenAnswer(inv -> {
            SettlementLine l = inv.getArgument(0);
            if (l.getId() == null) ReflectionTestUtils.setField(l, "id", 900L + l.getLineNo());
            return l;
        });
        lenient().when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(eq(ORG), anyLong()))
            .thenReturn(List.of());
        lenient().when(receiveDocumentRepo.save(any(ReceiveDocument.class))).thenAnswer(inv -> {
            ReceiveDocument d = inv.getArgument(0);
            if (d.getId() == null) ReflectionTestUtils.setField(d, "id", 500L);
            return d;
        });
        lenient().when(receiveDocumentRepo.findByIdAndOrgId(eq(500L), eq(ORG)))
            .thenAnswer(inv -> Optional.of(openDoc()));
        // assemble() lookups
        lenient().when(branchRepo.findByIdAndOrgId(BRANCH_ID, ORG)).thenReturn(Optional.of(branch()));
        lenient().when(customerRepo.findByIdAndOrgId(anyLong(), eq(ORG))).thenReturn(Optional.of(customer()));
        lenient().when(categoryRepo.findByIdAndOrgId(anyLong(), eq(ORG))).thenReturn(Optional.empty());
        lenient().when(statusRepo.findByIdAndOrgId(anyLong(), eq(ORG))).thenReturn(Optional.empty());
        lenient().when(settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of(mode()));
        lenient().when(bankRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG)).thenReturn(List.of());
        lenient().when(userRepo.findById(CASHIER_ID)).thenReturn(Optional.of(cashier()));
        lenient().when(settlementModeRepo.findByIdAndOrgId(eq(1L), eq(ORG))).thenReturn(Optional.of(mode()));
        lenient().when(attachmentRepo.countByOrgIdAndParentTypeAndParentId(any(), any(), any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void addPayment_appendsToTheOpenDocument_andNeverCreatesASecond() {
        ReceiveDocument open = openDoc();
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(open));

        service.addLine(500L, lineInput(new BigDecimal("3133")));

        ArgumentCaptor<SettlementLine> line = ArgumentCaptor.forClass(SettlementLine.class);
        verify(settlementLineRepo).save(line.capture());
        assertThat(line.getValue().getReceiveDocumentId()).isEqualTo(500L);
        // no NEW receive document was persisted (only the existing one is re-saved)
        ArgumentCaptor<ReceiveDocument> docs = ArgumentCaptor.forClass(ReceiveDocument.class);
        verify(receiveDocumentRepo).save(docs.capture());
        assertThat(docs.getValue().getId()).isEqualTo(500L);
    }

    @Test
    void addPayment_onAnApprovedDocument_reopensToSubmitted_withAnAuditEvent() {
        ReceiveDocument approved = openDoc(); // APPROVED by default
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(approved));

        service.addLine(500L, lineInput(new BigDecimal("500")));

        assertThat(approved.getWorkflowStatus()).isEqualTo(WorkflowStatus.SUBMITTED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> detail = ArgumentCaptor.forClass(java.util.Map.class);
        verify(auditService, org.mockito.Mockito.times(2)).recordUserEvent(
            eq("ReceiveDocument"), eq(500L), any(), any(), eq(CASHIER_ID), detail.capture());
        assertThat(detail.getAllValues()).anySatisfy(d -> assertThat(d).containsEntry("reopenedFrom", "APPROVED"));
    }

    @Test
    void addPayment_onAVerifiedDocument_reopensToSubmitted() {
        ReceiveDocument verified = openDoc();
        verified.setWorkflowStatus(WorkflowStatus.VERIFIED);
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(verified));

        service.addLine(500L, lineInput(new BigDecimal("500")));

        assertThat(verified.getWorkflowStatus()).isEqualTo(WorkflowStatus.SUBMITTED);
    }

    @Test
    void addPayment_onADraftDocument_isNotReopened_sinceItWasNeverSubmitted() {
        ReceiveDocument draft = draftDoc();
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(draft));

        service.addLine(500L, lineInput(new BigDecimal("500")));

        assertThat(draft.getWorkflowStatus()).isEqualTo(WorkflowStatus.DRAFT);
    }

    @Test
    void addPayment_onANonClaimJobCard_rejectsAZeroAmount() {
        ReceiveDocument open = openDoc();
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.addLine(500L, lineInput(BigDecimal.ZERO)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("greater than 0");
        verify(settlementLineRepo, never()).save(any(SettlementLine.class));
    }

    @Test
    void addPayment_onAWarrantyClaimJobCard_allowsAZeroAmount() {
        ReceiveDocument open = openDoc();
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(open));
        JobCard claimJobCard = jobCard(new BigDecimal("15431"));
        claimJobCard.setClaimTypeId(9L); // Warranty
        when(jobCardRepo.findByIdAndOrgId(JOB_CARD_ID, ORG)).thenReturn(Optional.of(claimJobCard));

        service.addLine(500L, lineInput(BigDecimal.ZERO));

        ArgumentCaptor<SettlementLine> line = ArgumentCaptor.forClass(SettlementLine.class);
        verify(settlementLineRepo).save(line.capture());
        assertThat(line.getValue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void create_onAJobCardWithAClosedClaim_isRejected() {
        when(claimCloseRepo.existsByOrgIdAndJobCardId(ORG, JOB_CARD_ID)).thenReturn(true);

        CreateReceiptRequest req = new CreateReceiptRequest();
        req.setJobCardId(JOB_CARD_ID);

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("closed claim");
    }

    @Test
    void create_alwaysPostsUnderTheJobCardBranch_notAnythingFromTheRequest() {
        when(receiveDocumentRepo.findByOrgIdAndJobCardIdAndSettledFalseAndWorkflowStatusNot(ORG, JOB_CARD_ID, WorkflowStatus.REJECTED))
            .thenReturn(Optional.empty());

        CreateReceiptRequest req = new CreateReceiptRequest();
        req.setJobCardId(JOB_CARD_ID);   // CreateReceiptRequest has no branch field at all

        service.create(req);

        ArgumentCaptor<ReceiveDocument> doc = ArgumentCaptor.forClass(ReceiveDocument.class);
        verify(receiveDocumentRepo, org.mockito.Mockito.atLeastOnce()).save(doc.capture());
        assertThat(doc.getAllValues())
            .allSatisfy(d -> assertThat(d.getBranchId()).isEqualTo(BRANCH_ID));
    }

    @Test
    void create_whenPreviousDocumentRejected_opensNewDraft() {
        when(receiveDocumentRepo.findByOrgIdAndJobCardIdAndSettledFalseAndWorkflowStatusNot(ORG, JOB_CARD_ID, WorkflowStatus.REJECTED))
            .thenReturn(Optional.empty());

        CreateReceiptRequest req = new CreateReceiptRequest();
        req.setJobCardId(JOB_CARD_ID);

        service.create(req);

        ArgumentCaptor<ReceiveDocument> docCaptor = ArgumentCaptor.forClass(ReceiveDocument.class);
        verify(receiveDocumentRepo, org.mockito.Mockito.atLeastOnce()).save(docCaptor.capture());
        verify(auditService).recordUserEvent(eq("ReceiveDocument"), any(), any(), eq(EventType.CREATED), eq(CASHIER_ID), any());
    }

    @Test
    void create_withNoExistingJobCard_carriesTheClaimTypeOntoTheNewJobCard() {
        long newJobCardId = 99L;
        JobCardResponse createdJobCard = mock(JobCardResponse.class);
        when(createdJobCard.id()).thenReturn(newJobCardId);
        when(jobCardService.create(any())).thenReturn(createdJobCard);
        JobCard newJobCard = jobCard(null);
        ReflectionTestUtils.setField(newJobCard, "id", newJobCardId);
        when(jobCardRepo.findByIdAndOrgId(newJobCardId, ORG)).thenReturn(Optional.of(newJobCard));
        when(receiveDocumentRepo.findByOrgIdAndJobCardIdAndSettledFalseAndWorkflowStatusNot(
            ORG, newJobCardId, WorkflowStatus.REJECTED)).thenReturn(Optional.empty());
        when(claimCloseRepo.existsByOrgIdAndJobCardId(ORG, newJobCardId)).thenReturn(false);
        when(paymentGuard.requireCanPost(eq(ORG), any(JobCard.class))).thenReturn(cashier());

        CreateReceiptRequest req = new CreateReceiptRequest();
        req.setCustomerName("Kaka Kalia");
        req.setCategoryId(1L);
        req.setBusinessStatusId(1L);
        req.setClaimTypeId(9L); // Warranty

        service.create(req);

        ArgumentCaptor<JobCardCreateRequest> jcReq = ArgumentCaptor.forClass(JobCardCreateRequest.class);
        verify(jobCardService).create(jcReq.capture());
        assertThat(jcReq.getValue().getClaimTypeId()).isEqualTo(9L);
    }

    @Test
    void submit_assignsAGapFreeNumber_stampsLineIds_andAuditsSubmitted() {
        ReceiveDocument draft = draftDoc();
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(draft));
        SettlementLine l1 = persistedLine(1);
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(ORG, 500L)).thenReturn(List.of(l1));
        when(documentNumberService.nextNumber(eq(ORG), any(Branch.class), eq(DocType.R)))
            .thenReturn("OOR-AUG26-R-001");

        service.submit(500L);

        assertThat(draft.getDocumentNo()).isEqualTo("OOR-AUG26-R-001");
        assertThat(draft.getWorkflowStatus()).isEqualTo(WorkflowStatus.SUBMITTED);
        assertThat(l1.getLineId()).isEqualTo("OOR-AUG26-R-001-L1");
        verify(auditService).recordUserEvent(eq("ReceiveDocument"), eq(500L), any(), eq(EventType.SUBMITTED),
            eq(CASHIER_ID), any());
    }

    @Test
    void document_autoSettles_withASystemEvent_whenPendingReachesZero() {
        ReceiveDocument open = openDoc();
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(open));
        when(pendingAmountCalculator.forJobCard(any(JobCard.class))).thenReturn(BigDecimal.ZERO);
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(ORG, 500L))
            .thenReturn(List.of(persistedLine(1)));

        service.addLine(500L, lineInput(new BigDecimal("3133")));

        assertThat(open.isSettled()).isTrue();
        verify(auditService).recordSystemEvent(eq("ReceiveDocument"), eq(500L), any(), eq(EventType.SETTLED), any());
        verify(attachmentService).freezeReceiveDocument(eq(ORG), eq(500L), any());
    }

    @Test
    void addPayment_toASettledDocument_isRejected() {
        ReceiveDocument settled = openDoc();
        settled.setSettled(true);
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(settled));

        assertThatThrownBy(() -> service.addLine(500L, lineInput(new BigDecimal("10"))))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("settled");
        verify(settlementLineRepo, never()).save(any());
    }

    @Test
    void submit_refusesCashLineInLockedDay_withoutConsumingNumber() {
        ReceiveDocument draft = draftDoc();
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(draft));
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(ORG, 500L))
            .thenReturn(List.of(persistedLine(1)));
        SettlementMode cashMode = mode();
        cashMode.setCash(true);
        lenient().when(settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(ORG))
            .thenReturn(List.of(cashMode));
        org.mockito.Mockito.doThrow(DamsException.conflict("A cash settlement line dated 2026-08-30 cannot be recorded"))
            .when(cashDateLock).requireCashLineDateOpen(eq(ORG), eq(BRANCH_ID), eq(LocalDate.of(2026, 8, 30)), eq(true), eq("settlement"));

        assertThatThrownBy(() -> service.submit(500L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("cash settlement line");
        // The lock check itself ran (not some earlier guard)…
        verify(cashDateLock).requireCashLineDateOpen(eq(ORG), eq(BRANCH_ID),
            eq(LocalDate.of(2026, 8, 30)), eq(true), eq("settlement"));
        // …and the refusal happens before numbering, so no document number is consumed.
        verify(documentNumberService, never()).nextNumber(any(), any(), any());
        assertThat(draft.getDocumentNo()).isNull();
        assertThat(draft.getWorkflowStatus()).isEqualTo(WorkflowStatus.DRAFT);
    }

    @Test
    void lineNumbers_neverReused_afterDelete() {
        ReceiveDocument doc = openDoc();
        doc.setWorkflowStatus(WorkflowStatus.QUERIED);
        when(receiveDocumentRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(doc));

        service.addLine(500L, lineInput(new BigDecimal("100")));
        service.addLine(500L, lineInput(new BigDecimal("200")));

        SettlementLine first = persistedLine(1);
        when(settlementLineRepo.findByOrgIdAndReceiveDocumentIdAndLineNo(ORG, 500L, 1))
            .thenReturn(Optional.of(first));
        service.deleteLine(500L, 1);

        service.addLine(500L, lineInput(new BigDecimal("300")));

        ArgumentCaptor<SettlementLine> lines = ArgumentCaptor.forClass(SettlementLine.class);
        verify(settlementLineRepo, org.mockito.Mockito.times(3)).save(lines.capture());
        assertThat(lines.getAllValues()).extracting(SettlementLine::getLineNo).containsExactly(1, 2, 3);
        assertThat(doc.getLineNoSeq()).isEqualTo(3);
    }

    // --- fixtures ---

    private static JobCard jobCard(BigDecimal invoice) {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", JOB_CARD_ID);
        jc.setOrgId(ORG);
        jc.setBranchId(BRANCH_ID);
        jc.setCustomerId(42L);
        jc.setCategoryId(1L);
        jc.setBusinessStatusId(1L);
        jc.setInvoiceAmount(invoice);
        return jc;
    }

    private static ReceiveDocument openDoc() {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", 500L);
        d.setOrgId(ORG);
        d.setBranchId(BRANCH_ID);
        d.setJobCardId(JOB_CARD_ID);
        d.setDocumentNo("OOR-JUL26-R-011");
        d.setWorkflowStatus(WorkflowStatus.APPROVED);
        d.setCreatedBy(CASHIER_ID);
        return d;
    }

    private static ReceiveDocument draftDoc() {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", 500L);
        d.setOrgId(ORG);
        d.setBranchId(BRANCH_ID);
        d.setJobCardId(JOB_CARD_ID);
        d.setWorkflowStatus(WorkflowStatus.DRAFT);
        d.setCreatedBy(CASHIER_ID);
        return d;
    }

    private static SettlementLine persistedLine(int no) {
        SettlementLine l = new SettlementLine();
        ReflectionTestUtils.setField(l, "id", 900L + no);
        l.setOrgId(ORG);
        l.setReceiveDocumentId(500L);
        l.setLineNo(no);
        l.setAmount(new BigDecimal("3133"));
        l.setSettlementModeId(1L);
        l.setTransactionDate(LocalDate.of(2026, 8, 30));
        return l;
    }

    private static SettlementLineInput lineInput(BigDecimal amount) {
        SettlementLineInput in = new SettlementLineInput();
        in.setTransactionDate(LocalDate.of(2026, 8, 30));
        in.setSettlementModeId(1L);
        in.setAmount(amount);
        return in;
    }

    private static AppUser cashier() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", CASHIER_ID);
        u.setName("Bikram Nayak");
        u.setRole(Role.CASHIER);
        u.setHomeBranchId(BRANCH_ID);
        return u;
    }

    private static Branch branch() {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", BRANCH_ID);
        b.setOrgId(ORG);
        b.setCode("OOR");
        b.setName("Rayagada");
        b.setActive(true);
        return b;
    }

    private static Customer customer() {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", 42L);
        c.setOrgId(ORG);
        c.setName("Sumati Sahoo");
        return c;
    }

    private static SettlementMode mode() {
        SettlementMode m = new SettlementMode();
        ReflectionTestUtils.setField(m, "id", 1L);
        m.setOrgId(ORG);
        m.setName("Cash");
        m.setActive(true);
        return m;
    }
}

