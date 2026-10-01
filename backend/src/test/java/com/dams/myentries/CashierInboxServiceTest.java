package com.dams.myentries;

import com.dams.audit.entity.AuditEvent;
import com.dams.audit.entity.EventType;
import com.dams.audit.repository.AuditEventRepository;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.entity.PreApprovalStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.myentries.dto.CashierInboxResponse;
import com.dams.myentries.service.CashierInboxService;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashierInboxServiceTest {

    private static final long ORG = 1L;
    private static final long ME = 7L;

    @Mock private ReceiveDocumentRepository receiveRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private ExpenseDocumentRepository expenseRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private CashDocumentRepository cashRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private ReceiverRepository receiverRepo;
    @Mock private AuditEventRepository auditRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private BranchScope branchScope;

    private CashierInboxService service;

    @BeforeEach
    void setUp() {
        service = new CashierInboxService(receiveRepo, settlementLineRepo, expenseRepo, expenseLineRepo, cashRepo,
            jobCardRepo, customerRepo, receiverRepo, auditRepo, userRepo, branchScope, new ObjectMapper());
        TenantContext.setOrgId(ORG);
        when(branchScope.currentUserId()).thenReturn(ME);
        when(receiveRepo.findByOrgIdAndCreatedByAndWorkflowStatusIn(anyLong(), anyLong(), any())).thenReturn(List.of());
        when(expenseRepo.findByOrgIdAndCreatedByAndWorkflowStatusIn(anyLong(), anyLong(), any())).thenReturn(List.of());
        when(cashRepo.findByOrgIdAndCreatedByAndWorkflowStatusIn(anyLong(), anyLong(), any())).thenReturn(List.of());
        when(expenseRepo.findByOrgIdAndCreatedByAndWorkflowStatusAndPreApprovalStatusIsNotNull(anyLong(), anyLong(), any()))
            .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ReceiveDocument receipt(long id, WorkflowStatus status) {
        ReceiveDocument d = new ReceiveDocument();
        ReflectionTestUtils.setField(d, "id", id);
        d.setOrgId(ORG);
        d.setJobCardId(60L);
        d.setWorkflowStatus(status);
        d.setCreatedBy(ME);
        return d;
    }

    private ExpenseDocument expense(long id, ExpenseWorkflowStatus status, PreApprovalStatus pre) {
        ExpenseDocument d = new ExpenseDocument();
        ReflectionTestUtils.setField(d, "id", id);
        d.setOrgId(ORG);
        d.setReceiverId(3L);
        d.setWorkflowStatus(status);
        d.setPreApprovalStatus(pre);
        d.setCreatedBy(ME);
        return d;
    }

    private AuditEvent event(EventType type, long actorId, String detail, Instant at) {
        AuditEvent e = new AuditEvent();
        e.setEventType(type);
        e.setActorId(actorId);
        e.setDetail(detail);
        e.setCreatedAt(at);
        return e;
    }

    private void actor(long id, String name, Role role) {
        AppUser u = new AppUser();
        u.setName(name);
        u.setRole(role);
        when(userRepo.findById(id)).thenReturn(Optional.of(u));
    }

    @Test
    void queriedReceipt_showsWhoAskedWhat_andCountsAsUnattended() {
        when(receiveRepo.findByOrgIdAndCreatedByAndWorkflowStatusIn(anyLong(), anyLong(), any()))
            .thenReturn(List.of(receipt(11L, WorkflowStatus.QUERIED)));
        when(auditRepo.findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(ORG, "ReceiveDocument", 11L))
            .thenReturn(List.of(event(EventType.QUERIED, 9L, "{\"note\":\"Amount does not match the invoice\"}", Instant.now())));
        actor(9L, "Anita", Role.ACCOUNTANT);

        CashierInboxResponse r = service.inbox();

        assertThat(r.queriesToAct()).isEqualTo(1);
        CashierInboxResponse.Item item = r.queries().get(0);
        assertThat(item.kind()).isEqualTo("RECEIPT");
        assertThat(item.fromName()).isEqualTo("Anita");
        assertThat(item.fromRole()).isEqualTo("ACCOUNTANT");
        assertThat(item.note()).isEqualTo("Amount does not match the invoice");
        assertThat(item.needsAction()).isTrue();
    }

    @Test
    void oldRejection_isHidden_recentRejection_isShownButNotCounted() {
        when(receiveRepo.findByOrgIdAndCreatedByAndWorkflowStatusIn(anyLong(), anyLong(), any()))
            .thenReturn(List.of(receipt(21L, WorkflowStatus.REJECTED), receipt(22L, WorkflowStatus.REJECTED)));
        when(auditRepo.findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(ORG, "ReceiveDocument", 21L))
            .thenReturn(List.of(event(EventType.REJECTED, 9L, "{\"reason\":\"Duplicate\"}", Instant.now().minus(Duration.ofDays(30)))));
        when(auditRepo.findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(ORG, "ReceiveDocument", 22L))
            .thenReturn(List.of(event(EventType.REJECTED, 9L, "{\"reason\":\"Wrong branch\"}", Instant.now().minus(Duration.ofDays(1)))));
        actor(9L, "Anita", Role.ACCOUNTANT);

        CashierInboxResponse r = service.inbox();

        assertThat(r.queries()).extracting(CashierInboxResponse.Item::id).containsExactly(22L);
        assertThat(r.queries().get(0).state()).isEqualTo("REJECTED");
        assertThat(r.queriesToAct()).isZero();
    }

    @Test
    void preApprovalLifecycle_pendingIsWaiting_approvedAndQueriedNeedTheCashier() {
        when(expenseRepo.findByOrgIdAndCreatedByAndWorkflowStatusAndPreApprovalStatusIsNotNull(anyLong(), anyLong(), any()))
            .thenReturn(List.of(
                expense(31L, ExpenseWorkflowStatus.DRAFT, PreApprovalStatus.PENDING),
                expense(32L, ExpenseWorkflowStatus.DRAFT, PreApprovalStatus.APPROVED),
                expense(33L, ExpenseWorkflowStatus.DRAFT, PreApprovalStatus.QUERIED)));
        when(auditRepo.findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(ORG, "ExpenseDocument", 32L))
            .thenReturn(List.of(event(EventType.PRE_APPROVED, 5L, "{\"amount\":12000}", Instant.now())));
        when(auditRepo.findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(ORG, "ExpenseDocument", 33L))
            .thenReturn(List.of(
                event(EventType.QUERIED, 5L, "{\"preApproval\":true,\"note\":\"Why so high?\"}", Instant.now())));
        actor(5L, "Farah", Role.FINANCE_MANAGER);

        CashierInboxResponse r = service.inbox();

        assertThat(r.approvals()).hasSize(3);
        assertThat(r.approvalsToAct()).isEqualTo(2);
        assertThat(r.approvals()).filteredOn(i -> i.id() == 31L).singleElement()
            .satisfies(i -> { assertThat(i.state()).isEqualTo("PRE_PENDING"); assertThat(i.needsAction()).isFalse(); });
        assertThat(r.approvals()).filteredOn(i -> i.id() == 33L).singleElement()
            .satisfies(i -> { assertThat(i.state()).isEqualTo("PRE_QUERIED"); assertThat(i.note()).isEqualTo("Why so high?"); });
    }

    @Test
    void nothingPending_meansEmptyBoxesAndZeroBadges() {
        CashierInboxResponse r = service.inbox();
        assertThat(r.queries()).isEmpty();
        assertThat(r.approvals()).isEmpty();
        assertThat(r.queriesToAct()).isZero();
        assertThat(r.approvalsToAct()).isZero();
    }

    @SuppressWarnings("unused")
    private static BigDecimal unused() {
        return BigDecimal.ZERO;
    }
}
