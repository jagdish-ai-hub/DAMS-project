package com.dams.cash;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.dto.CashDayCloseResponse;
import com.dams.cash.dto.CloseDayRequest;
import com.dams.cash.dto.ReopenDayRequest;
import com.dams.cash.entity.CashDayClose;
import com.dams.cash.repository.BranchCashOpeningRepository;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.cash.service.CashCloseService;
import com.dams.cash.service.CashDocumentService;
import com.dams.cash.service.CashPostingGuard;
import com.dams.cash.service.DrawerService;
import com.dams.common.exception.DamsException;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
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
import java.util.Map;
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
 * Day-close rules: variance = counted − computed, a remark is mandatory when the variance is
 * non-zero, a future date is refused, and a day already covered by a later close cannot be
 * closed again.
 */
@ExtendWith(MockitoExtension.class)
class CashCloseServiceTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
    private static final long OWNER_ID = 2L;
    private static final long BRANCH = 3L;
    private static final LocalDate CLOSE_DATE = LocalDate.of(2026, 7, 30);

    @Mock private CashDayCloseRepository cashDayCloseRepo;
    @Mock private BranchCashOpeningRepository branchCashOpeningRepo;
    @Mock private CashDocumentRepository cashDocumentRepo;
    @Mock private DrawerService drawerService;
    @Mock private CashDocumentService cashDocumentService;
    @Mock private BranchRepository branchRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private CashPostingGuard guard;
    @Mock private AuditService auditService;

    private CashCloseService service;

    @BeforeEach
    void setUp() {
        service = new CashCloseService(cashDayCloseRepo, branchCashOpeningRepo, cashDocumentRepo,
            drawerService, cashDocumentService, branchRepo, userRepo, guard, auditService);
        TenantContext.setOrgId(ORG);

        lenient().when(guard.requireCashier(ORG)).thenReturn(cashier());
        lenient().when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(ORG, BRANCH))
            .thenReturn(Optional.empty());
        lenient().when(drawerService.position(ORG, BRANCH, CLOSE_DATE)).thenReturn(
            new DrawerService.DrawerPosition(new BigDecimal("10000"), true,
                new BigDecimal("2000"), new BigDecimal("20000"),
                new BigDecimal("1000"), new BigDecimal("6000"),
                new BigDecimal("25000")));
        lenient().when(branchRepo.findByIdAndOrgId(BRANCH, ORG)).thenReturn(Optional.of(branch()));
        lenient().when(userRepo.findById(CASHIER_ID)).thenReturn(Optional.of(cashier()));
        lenient().when(cashDayCloseRepo.save(any(CashDayClose.class))).thenAnswer(inv -> {
            CashDayClose c = inv.getArgument(0);
            if (c.getId() == null) ReflectionTestUtils.setField(c, "id", 500L);
            return c;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void closeDay_withNoVariance_savesWithoutARemark() {
        CashDayCloseResponse r = service.closeDay(request(new BigDecimal("25000"), null));

        assertThat(r.variance()).isEqualByComparingTo("0");
        assertThat(r.varianceRemark()).isNull();
        verify(auditService).recordUserEvent(eq("CashDayClose"), eq(500L), eq(EventType.CLOSED), eq(CASHIER_ID), any());
    }

    @Test
    void closeDay_withVarianceButNoRemark_isRejected() {
        assertThatThrownBy(() -> service.closeDay(request(new BigDecimal("24000"), "   ")))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("variance remark is required");
        verify(cashDayCloseRepo, never()).save(any());
    }

    @Test
    void closeDay_withVarianceAndRemark_recordsTheVariance() {
        CashDayCloseResponse r = service.closeDay(request(new BigDecimal("24000"), "Counted short — investigating"));

        assertThat(r.variance()).isEqualByComparingTo("-1000");
        assertThat(r.varianceRemark()).isEqualTo("Counted short — investigating");
        assertThat(r.computedClosing()).isEqualByComparingTo("25000");
    }

    @Test
    void closeDay_forAFutureDate_isRejected() {
        assertThatThrownBy(() -> service.closeDay(request(OrgTime.today().plusDays(1), new BigDecimal("10"), null)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("future date");
    }

    @Test
    void closeDay_whenAlreadyClosedThroughALaterDate_isRejected() {
        CashDayClose later = new CashDayClose();
        later.setBranchId(BRANCH);
        later.setCloseDate(CLOSE_DATE.plusDays(2));
        when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(ORG, BRANCH))
            .thenReturn(Optional.of(later));

        assertThatThrownBy(() -> service.closeDay(request(new BigDecimal("25000"), null)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("already closed through");
        verify(cashDayCloseRepo, never()).save(any());
    }

    // ------------------------------------------------------------ reopen (rev 60)

    @Test
    void reopenDay_latestClose_deletesTheRowAndKeepsTheOriginalInTheAudit() {
        CashDayClose latest = existingClose(500L, CLOSE_DATE);
        when(guard.requireOwner(ORG)).thenReturn(owner());
        when(cashDayCloseRepo.findByOrgIdAndBranchIdAndCloseDate(ORG, BRANCH, CLOSE_DATE)).thenReturn(Optional.of(latest));
        when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(ORG, BRANCH)).thenReturn(Optional.of(latest));
        when(guard.resolveViewBranch(ORG, BRANCH)).thenReturn(BRANCH);
        when(drawerService.position(ORG, BRANCH, CLOSE_DATE)).thenReturn(
            new DrawerService.DrawerPosition(BigDecimal.ZERO, true, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(drawerService.lineBreakdown(eq(ORG), eq(BRANCH), eq(CLOSE_DATE), any()))
            .thenReturn(new DrawerService.DrawerLines(java.util.List.of(), java.util.List.of()));

        service.reopenDay(reopenRequest("Closed too early"));

        verify(cashDayCloseRepo).delete(latest);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detail = ArgumentCaptor.forClass(Map.class);
        verify(auditService).recordUserEvent(eq("CashDayClose"), eq(500L), eq(BRANCH),
            eq(EventType.CASH_REOPENED), eq(OWNER_ID), detail.capture());
        assertThat(detail.getValue())
            .containsEntry("closeDate", CLOSE_DATE.toString())
            .containsEntry("reason", "Closed too early")
            .containsEntry("closedBy", CASHIER_ID);
        assertThat((BigDecimal) detail.getValue().get("countedAmount")).isEqualByComparingTo("25000");
    }

    @Test
    void reopenDay_anEarlierCloseUnderALaterOne_isRefused() {
        CashDayClose earlier = existingClose(500L, CLOSE_DATE);
        CashDayClose later = existingClose(501L, CLOSE_DATE.plusDays(1));
        when(guard.requireOwner(ORG)).thenReturn(owner());
        when(cashDayCloseRepo.findByOrgIdAndBranchIdAndCloseDate(ORG, BRANCH, CLOSE_DATE)).thenReturn(Optional.of(earlier));
        when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(ORG, BRANCH)).thenReturn(Optional.of(later));

        assertThatThrownBy(() -> service.reopenDay(reopenRequest("oops")))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Only the latest close can be reopened");
        verify(cashDayCloseRepo, never()).delete(any(CashDayClose.class));
        verify(auditService, never()).recordUserEvent(any(), any(), any(), eq(EventType.CASH_REOPENED), any(), any());
    }

    @Test
    void reopenDay_aDayThatIsNotClosed_isRefused() {
        when(guard.requireOwner(ORG)).thenReturn(owner());
        when(cashDayCloseRepo.findByOrgIdAndBranchIdAndCloseDate(ORG, BRANCH, CLOSE_DATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reopenDay(reopenRequest("oops")))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("is not closed");
        verify(cashDayCloseRepo, never()).delete(any(CashDayClose.class));
    }

    @Test
    void reopenDay_isRefusedForAnyoneButTheOwner() {
        when(guard.requireOwner(ORG)).thenThrow(DamsException.forbidden("Only the Owner can reopen a closed cash day"));

        assertThatThrownBy(() -> service.reopenDay(reopenRequest("oops"))).isInstanceOf(DamsException.class);
        verify(cashDayCloseRepo, never()).delete(any(CashDayClose.class));
    }

    private static ReopenDayRequest reopenRequest(String reason) {
        ReopenDayRequest r = new ReopenDayRequest();
        r.setBranchId(BRANCH);
        r.setCloseDate(CLOSE_DATE);
        r.setReason(reason);
        return r;
    }

    private static CashDayClose existingClose(long id, LocalDate date) {
        CashDayClose c = new CashDayClose();
        ReflectionTestUtils.setField(c, "id", id);
        c.setOrgId(ORG);
        c.setBranchId(BRANCH);
        c.setCloseDate(date);
        c.setOpeningAmount(new BigDecimal("10000"));
        c.setComputedClosing(new BigDecimal("25000"));
        c.setCountedAmount(new BigDecimal("25000"));
        c.setVariance(BigDecimal.ZERO);
        c.setClosedBy(CASHIER_ID);
        return c;
    }

    private static AppUser owner() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", OWNER_ID);
        u.setName("Priya Nair");
        u.setRole(Role.OWNER);
        return u;
    }

    private static CloseDayRequest request(BigDecimal counted, String remark) {
        return request(CLOSE_DATE, counted, remark);
    }

    private static CloseDayRequest request(LocalDate date, BigDecimal counted, String remark) {
        CloseDayRequest r = new CloseDayRequest();
        r.setCloseDate(date);
        r.setCountedAmount(counted);
        r.setVarianceRemark(remark);
        return r;
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

    // --- Branch opening (rev 97) ---

    private static Branch branchNamed(long id, String code, String name, boolean active) {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", id);
        b.setOrgId(ORG);
        b.setCode(code);
        b.setName(name);
        b.setActive(active);
        return b;
    }

    private static com.dams.cash.dto.CashOpeningRequest openingRequest(long branchId, LocalDate date, String amount) {
        com.dams.cash.dto.CashOpeningRequest r = new com.dams.cash.dto.CashOpeningRequest();
        r.setBranchId(branchId);
        r.setOpeningDate(date);
        r.setAmount(new BigDecimal(amount));
        return r;
    }

    @Test
    void openings_listsOnlyVisibleActiveBranches_markingSetAndLockedOnes() {
        Branch mine = branchNamed(6L, "OOJ", "Jeypore", true);
        Branch setAlready = branchNamed(7L, "OOB", "Berhampur", true);
        Branch closedOnly = branchNamed(8L, "OOR", "Rayagada", true);
        Branch notMine = branchNamed(9L, "OOX", "Elsewhere", true);
        Branch inactive = branchNamed(10L, "OOY", "Shut", false);
        when(branchRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(java.util.List.of(mine, setAlready, closedOnly, notMine, inactive));
        for (long id : new long[] {6L, 7L, 8L}) {
            lenient().when(guard.canSeeBranch(id)).thenReturn(true);
        }
        lenient().when(guard.canSeeBranch(9L)).thenReturn(false);
        com.dams.cash.entity.BranchCashOpening o = new com.dams.cash.entity.BranchCashOpening();
        o.setOrgId(ORG);
        o.setBranchId(7L);
        o.setOpeningDate(LocalDate.of(2026, 11, 2));
        o.setAmount(new BigDecimal("5000"));
        when(branchCashOpeningRepo.findByOrgId(ORG)).thenReturn(java.util.List.of(o));
        lenient().when(cashDayCloseRepo.existsByOrgIdAndBranchId(ORG, 8L)).thenReturn(true);

        java.util.List<com.dams.cash.dto.BranchOpeningResponse> rows = service.openings();

        assertThat(rows).extracting(com.dams.cash.dto.BranchOpeningResponse::branchCode).containsExactly("OOJ", "OOB", "OOR");
        assertThat(rows.get(0).set()).isFalse();
        assertThat(rows.get(0).locked()).isFalse();           // not set, never closed: the form is offered
        assertThat(rows.get(1).set()).isTrue();
        assertThat(rows.get(1).amount()).isEqualByComparingTo("5000");
        assertThat(rows.get(1).openingDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(rows.get(1).locked()).isTrue();
        assertThat(rows.get(2).set()).isFalse();
        assertThat(rows.get(2).locked()).isTrue();             // already closed days: too late to set an opening
    }

    @Test
    void setOpening_recordsTheOneTimeOpening_andAuditsIt() {
        when(guard.requireAccountantForBranch(ORG, BRANCH)).thenReturn(cashier());
        // setOpening answers with the branch's drawer for that day
        lenient().when(guard.resolveViewBranch(ORG, BRANCH)).thenReturn(BRANCH);
        lenient().when(drawerService.position(eq(ORG), eq(BRANCH), any(LocalDate.class))).thenReturn(
            new DrawerService.DrawerPosition(BigDecimal.ZERO, true, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        lenient().when(drawerService.lineBreakdown(eq(ORG), eq(BRANCH), any(LocalDate.class), any()))
            .thenReturn(new DrawerService.DrawerLines(java.util.List.of(), java.util.List.of()));
        when(branchCashOpeningRepo.existsByOrgIdAndBranchId(ORG, BRANCH)).thenReturn(false);
        when(cashDayCloseRepo.existsByOrgIdAndBranchId(ORG, BRANCH)).thenReturn(false);
        when(branchCashOpeningRepo.save(any(com.dams.cash.entity.BranchCashOpening.class))).thenAnswer(inv -> {
            com.dams.cash.entity.BranchCashOpening saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 90L);
            return saved;
        });
        LocalDate day = OrgTime.today().minusDays(3);

        service.setOpening(openingRequest(BRANCH, day, "0"));

        ArgumentCaptor<com.dams.cash.entity.BranchCashOpening> saved = ArgumentCaptor.forClass(com.dams.cash.entity.BranchCashOpening.class);
        verify(branchCashOpeningRepo).save(saved.capture());
        assertThat(saved.getValue().getOpeningDate()).isEqualTo(day);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("0");     // a branch may start with an empty drawer
        verify(auditService).recordUserEvent(eq("BranchCashOpening"), eq(90L), eq(EventType.CREATED), any(), any());
    }

    @Test
    void setOpening_refusesAFutureDate_andSavesNothing() {
        when(guard.requireAccountantForBranch(ORG, BRANCH)).thenReturn(cashier());

        assertThatThrownBy(() -> service.setOpening(openingRequest(BRANCH, OrgTime.today().plusDays(1), "1000")))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("future");
        verify(branchCashOpeningRepo, never()).save(any());
    }

    @Test
    void setOpening_refusesASecondOpening_orOneAfterDaysHaveClosed() {
        when(guard.requireAccountantForBranch(ORG, BRANCH)).thenReturn(cashier());
        when(branchCashOpeningRepo.existsByOrgIdAndBranchId(ORG, BRANCH)).thenReturn(true);

        assertThatThrownBy(() -> service.setOpening(openingRequest(BRANCH, OrgTime.today(), "1000")))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("cannot be set after");
        verify(branchCashOpeningRepo, never()).save(any());
    }
}
