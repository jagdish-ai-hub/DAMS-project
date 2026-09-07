package com.dams.cash;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.dto.CreateReopenRequest;
import com.dams.cash.dto.ReopenRequestResponse;
import com.dams.cash.entity.CashCloseReopenRequest;
import com.dams.cash.entity.CashDayClose;
import com.dams.cash.entity.ReopenRequestStatus;
import com.dams.cash.repository.CashCloseReopenRequestRepository;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.cash.service.CashPostingGuard;
import com.dams.cash.service.CashReopenService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cash-close reopen request flow: the cashier files a PENDING request for a locked day,
 * the FM's approval removes the close (audited, never silent) so the day can be
 * re-closed, and rejection keeps the lock with a reason. Maker-checker holds throughout.
 */
@ExtendWith(MockitoExtension.class)
class CashReopenServiceTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
    private static final long FM_ID = 8L;
    private static final long BRANCH = 3L;
    private static final LocalDate CLOSE_DATE = LocalDate.of(2026, 7, 30);

    @Mock private CashCloseReopenRequestRepository reopenRepo;
    @Mock private CashDayCloseRepository cashDayCloseRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private BranchScope branchScope;
    @Mock private CashPostingGuard postingGuard;
    @Mock private AuditService auditService;

    private CashReopenService service;

    @BeforeEach
    void setUp() {
        service = new CashReopenService(reopenRepo, cashDayCloseRepo, branchRepo, userRepo,
            branchScope, postingGuard, auditService);
        TenantContext.setOrgId(ORG);

        lenient().when(postingGuard.requireCashier(ORG)).thenReturn(cashier());
        lenient().when(branchScope.currentUserId()).thenReturn(FM_ID);
        lenient().when(userRepo.findByIdAndOrganization_Id(FM_ID, ORG))
            .thenReturn(Optional.of(financeManager()));
        lenient().when(branchScope.canSeeBranch(BRANCH)).thenReturn(true);
        lenient().when(branchRepo.findByIdAndOrgId(BRANCH, ORG)).thenReturn(Optional.of(branch()));
        lenient().when(reopenRepo.save(any(CashCloseReopenRequest.class)))
            .thenAnswer(i -> {
                CashCloseReopenRequest r = i.getArgument(0);
                if (r.getId() == null) {
                    ReflectionTestUtils.setField(r, "id", 200L);
                }
                return r;
            });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void request_createsPendingRequest_whenDayIsClosed() {
        when(cashDayCloseRepo.findByOrgIdAndBranchIdAndCloseDate(ORG, BRANCH, CLOSE_DATE))
            .thenReturn(Optional.of(close()));
        when(reopenRepo.existsByOrgIdAndBranchIdAndCloseDateAndStatus(
            ORG, BRANCH, CLOSE_DATE, ReopenRequestStatus.PENDING)).thenReturn(false);

        ReopenRequestResponse response = service.request(createRequest());

        assertThat(response.status()).isEqualTo(ReopenRequestStatus.PENDING);
        assertThat(response.branchId()).isEqualTo(BRANCH);
        assertThat(response.closeDate()).isEqualTo(CLOSE_DATE);
        verify(auditService).recordUserEvent(eq("CashCloseReopenRequest"), eq(200L), eq(BRANCH),
            eq(EventType.CREATED), eq(CASHIER_ID), any());
    }

    @Test
    void request_conflict_whenDayIsNotClosed() {
        when(cashDayCloseRepo.findByOrgIdAndBranchIdAndCloseDate(ORG, BRANCH, CLOSE_DATE))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(createRequest()))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("is not closed");
        verify(reopenRepo, never()).save(any());
    }

    @Test
    void approve_deletesCloseAndMarksApproved_withAudit() {
        CashCloseReopenRequest row = pendingRequest();
        when(reopenRepo.findByIdAndOrgId(200L, ORG)).thenReturn(Optional.of(row));
        CashDayClose close = close();
        when(cashDayCloseRepo.findByOrgIdAndBranchIdAndCloseDate(ORG, BRANCH, CLOSE_DATE))
            .thenReturn(Optional.of(close));

        ReopenRequestResponse response = service.approve(200L);

        assertThat(response.status()).isEqualTo(ReopenRequestStatus.APPROVED);
        verify(cashDayCloseRepo).delete(close);
        verify(auditService).recordUserEvent(eq("CashCloseReopenRequest"), eq(200L), eq(BRANCH),
            eq(EventType.APPROVED), eq(FM_ID), any());
        verify(auditService).recordUserEvent(eq("CashDayClose"), eq(500L), eq(BRANCH),
            eq(EventType.CLOSED), eq(FM_ID), any());
    }

    @Test
    void reject_keepsCloseAndMarksRejected_withReason() {
        CashCloseReopenRequest row = pendingRequest();
        when(reopenRepo.findByIdAndOrgId(200L, ORG)).thenReturn(Optional.of(row));

        ReopenRequestResponse response = service.reject(200L, "Variance already explained");

        assertThat(response.status()).isEqualTo(ReopenRequestStatus.REJECTED);
        assertThat(response.decisionNote()).isEqualTo("Variance already explained");
        verify(cashDayCloseRepo, never()).delete(any());
        verify(auditService).recordUserEvent(eq("CashCloseReopenRequest"), eq(200L), eq(BRANCH),
            eq(EventType.REJECTED), eq(FM_ID), any());
    }

    @Test
    void approve_refusesOwnRequest_makerChecker() {
        CashCloseReopenRequest row = pendingRequest();
        row.setRequestedBy(FM_ID);
        when(reopenRepo.findByIdAndOrgId(200L, ORG)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.approve(200L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("you requested it");
        verify(cashDayCloseRepo, never()).delete(any());
    }

    private static CreateReopenRequest createRequest() {
        CreateReopenRequest r = new CreateReopenRequest();
        r.setCloseDate(CLOSE_DATE);
        r.setReason("Counted again — found a missed receipt");
        return r;
    }

    private static CashCloseReopenRequest pendingRequest() {
        CashCloseReopenRequest r = new CashCloseReopenRequest();
        ReflectionTestUtils.setField(r, "id", 200L);
        r.setOrgId(ORG);
        r.setBranchId(BRANCH);
        r.setCloseDate(CLOSE_DATE);
        r.setReason("Counted again — found a missed receipt");
        r.setStatus(ReopenRequestStatus.PENDING);
        r.setRequestedBy(CASHIER_ID);
        return r;
    }

    private static CashDayClose close() {
        CashDayClose c = new CashDayClose();
        ReflectionTestUtils.setField(c, "id", 500L);
        c.setOrgId(ORG);
        c.setBranchId(BRANCH);
        c.setCloseDate(CLOSE_DATE);
        c.setOpeningAmount(new BigDecimal("10000"));
        c.setComputedClosing(new BigDecimal("25000"));
        c.setCountedAmount(new BigDecimal("25000"));
        c.setVariance(BigDecimal.ZERO);
        c.setClosedBy(CASHIER_ID);
        return c;
    }

    private static AppUser cashier() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", CASHIER_ID);
        u.setRole(Role.CASHIER);
        u.setHomeBranchId(BRANCH);
        return u;
    }

    private static AppUser financeManager() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", FM_ID);
        u.setRole(Role.FINANCE_MANAGER);
        return u;
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
