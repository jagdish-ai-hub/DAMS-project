package com.dams.cash;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.entity.DocType;
import com.dams.branch.repository.BranchRepository;
import com.dams.branch.service.DocumentNumberService;
import com.dams.cash.dto.CashDocumentResponse;
import com.dams.cash.dto.CreateCashDocumentRequest;
import com.dams.cash.entity.CashDirection;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.entity.CashWorkflowStatus;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.cash.service.CashDateLock;
import com.dams.cash.service.CashDocumentService;
import com.dams.cash.service.CashPostingGuard;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.masters.repository.BankRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cash-movement rules for Stage 6: the C number is assigned on submit, a movement dated into
 * an already-closed cash day is refused, and only a queried movement can be resubmitted.
 */
@ExtendWith(MockitoExtension.class)
class CashDocumentServiceTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
    private static final long BRANCH = 3L;
    private static final long DOC_ID = 400L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 30);

    @Mock private CashDocumentRepository cashDocumentRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private BankRepository bankRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private DocumentNumberService documentNumberService;
    @Mock private CashDateLock cashDateLock;
    @Mock private CashPostingGuard guard;
    @Mock private AuditService auditService;
    @Mock private com.dams.audit.service.DocumentHistoryService documentHistoryService;

    private CashDocumentService service;

    @BeforeEach
    void setUp() {
        service = new CashDocumentService(cashDocumentRepo, branchRepo, bankRepo, userRepo,
            documentNumberService, cashDateLock, guard, auditService, documentHistoryService);
        TenantContext.setOrgId(ORG);

        lenient().when(guard.requireCashier(ORG)).thenReturn(cashier());
        lenient().when(branchRepo.findByIdAndOrgId(BRANCH, ORG)).thenReturn(Optional.of(branch()));
        lenient().when(userRepo.findById(CASHIER_ID)).thenReturn(Optional.of(cashier()));
        lenient().when(cashDocumentRepo.save(any(CashDocument.class))).thenAnswer(inv -> {
            CashDocument d = inv.getArgument(0);
            if (d.getId() == null) ReflectionTestUtils.setField(d, "id", DOC_ID);
            return d;
        });
        lenient().when(documentNumberService.nextNumber(eq(ORG), any(Branch.class), eq(DocType.C)))
            .thenReturn("OOR-AUG26-C-001");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_withSubmit_assignsAGapFreeCNumber_andAuditsSubmitted() {
        CashDocumentResponse r = service.create(request(true));

        assertThat(r.documentNo()).isEqualTo("OOR-AUG26-C-001");
        assertThat(r.workflowStatus()).isEqualTo("SUBMITTED");
        assertThat(r.direction()).isEqualTo("IN");
        verify(auditService).recordUserEvent(eq("CashDocument"), eq(DOC_ID), any(), eq(EventType.SUBMITTED),
            eq(CASHIER_ID), any());
    }

    @Test
    void create_savedAsDraft_hasNoNumberYet() {
        CashDocumentResponse r = service.create(request(false));

        assertThat(r.documentNo()).isNull();
        assertThat(r.workflowStatus()).isEqualTo("DRAFT");
    }

    @Test
    void create_isRefused_whenTheCashDayIsAlreadyClosed() {
        doThrow(DamsException.conflict("OOR's cash is closed through 2026-08-31"))
            .when(cashDateLock).requireCashDateOpen(eq(ORG), eq(BRANCH), any());

        assertThatThrownBy(() -> service.create(request(true)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("closed through");
        verify(cashDocumentRepo, never()).save(any());
    }

    @Test
    void submit_onANonDraft_isRejected() {
        when(cashDocumentRepo.findByIdAndOrgId(DOC_ID, ORG)).thenReturn(Optional.of(doc(CashWorkflowStatus.SUBMITTED)));

        assertThatThrownBy(() -> service.submit(DOC_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Only a draft");
    }

    @Test
    void resubmit_movesAQueriedMovementBackToSubmitted_butRejectsOthers() {
        when(cashDocumentRepo.findByIdAndOrgId(DOC_ID, ORG)).thenReturn(Optional.of(doc(CashWorkflowStatus.QUERIED)));
        CashDocumentResponse r = service.resubmit(DOC_ID);
        assertThat(r.workflowStatus()).isEqualTo("SUBMITTED");

        when(cashDocumentRepo.findByIdAndOrgId(DOC_ID, ORG)).thenReturn(Optional.of(doc(CashWorkflowStatus.APPROVED)));
        assertThatThrownBy(() -> service.resubmit(DOC_ID))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Only a queried");
    }

    private static CreateCashDocumentRequest request(boolean submit) {
        CreateCashDocumentRequest r = new CreateCashDocumentRequest();
        r.setDirection(CashDirection.IN);
        r.setTransactionDate(DATE);
        r.setAmount(new BigDecimal("20000"));
        r.setSubmit(submit);
        return r;
    }

    private static CashDocument doc(CashWorkflowStatus status) {
        CashDocument d = new CashDocument();
        ReflectionTestUtils.setField(d, "id", DOC_ID);
        d.setOrgId(ORG);
        d.setBranchId(BRANCH);
        d.setDirection(CashDirection.OUT);
        d.setTransactionDate(DATE);
        d.setAmount(new BigDecimal("5000"));
        d.setWorkflowStatus(status);
        if (status != CashWorkflowStatus.DRAFT) {
            d.setDocumentNo("OOR-JUL26-C-004");
        }
        d.setCreatedBy(CASHIER_ID);
        return d;
    }

    private static AppUser cashier() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", CASHIER_ID);
        u.setName("Bikram Nayak");
        u.setRole(Role.CASHIER);
        u.setHomeBranchId(BRANCH);
        return u;
    }

    private static Branch branch() {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", BRANCH);
        b.setOrgId(ORG);
        b.setCode("OOR");
        b.setName("Rayagada");
        b.setActive(true);
        return b;
    }
}
