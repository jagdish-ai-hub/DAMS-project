package com.dams.cash;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.dto.CashDayCloseResponse;
import com.dams.cash.dto.CloseDayRequest;
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
 * Day-close rules: variance = counted − computed, a remark is mandatory when the variance is
 * non-zero, a future date is refused, and a day already covered by a later close cannot be
 * closed again.
 */
@ExtendWith(MockitoExtension.class)
class CashCloseServiceTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
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
}
