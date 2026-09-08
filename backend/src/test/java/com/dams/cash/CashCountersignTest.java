package com.dams.cash;

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
import com.dams.config.TenantContext;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * FEAT-41: a second pair of eyes on big variances. A breach parks the close
 * PENDING instead of locking clean; the accountant confirms (never the
 * cashier who counted). Below threshold — or feature off — nothing changes.
 */
@ExtendWith(MockitoExtension.class)
class CashCountersignTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
    private static final long ACCOUNTANT_ID = 50L;
    private static final long BRANCH = 3L;
    private static final LocalDate DAY = LocalDate.of(2026, 7, 30);

    @Mock private CashDayCloseRepository cashDayCloseRepo;
    @Mock private BranchCashOpeningRepository branchCashOpeningRepo;
    @Mock private CashDocumentRepository cashDocumentRepo;
    @Mock private DrawerService drawerService;
    @Mock private CashDocumentService cashDocumentService;
    @Mock private BranchRepository branchRepo;
    @Mock private AppUserRepository userRepo;
    @Mock private CashPostingGuard guard;
    @Mock private AuditService auditService;
    @Mock private OrganizationRepository orgRepo;

    private CashCloseService service;

    @BeforeEach
    void setUp() {
        service = new CashCloseService(cashDayCloseRepo, branchCashOpeningRepo, cashDocumentRepo,
            drawerService, cashDocumentService, branchRepo, userRepo, guard, auditService, orgRepo);
        TenantContext.setOrgId(ORG);
        lenient().when(guard.requireCashier(ORG)).thenReturn(cashier());
        lenient().when(cashDayCloseRepo.findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(ORG, BRANCH))
            .thenReturn(Optional.empty());
        // Computed drawer position: 39200.
        lenient().when(drawerService.position(ORG, BRANCH, DAY)).thenReturn(
            new DrawerService.DrawerPosition(new BigDecimal("30000"), true,
                new BigDecimal("5000"), new BigDecimal("20000"),
                new BigDecimal("800"), new BigDecimal("15000"),
                new BigDecimal("39200")));
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
    void closeDay_breachAboveThreshold_parksPendingCountersign() {
        when(orgRepo.findById(ORG)).thenReturn(Optional.of(org(threshold("2000"))));

        CashDayCloseResponse r = service.closeDay(request(new BigDecimal("42200"), "Counted excess — recounting tomorrow"));

        assertThat(r.variance()).isEqualByComparingTo("3000");
        assertThat(r.countersignStatus()).isEqualTo("PENDING");
    }

    @Test
    void closeDay_belowThreshold_locksClean() {
        when(orgRepo.findById(ORG)).thenReturn(Optional.of(org(threshold("5000"))));

        CashDayCloseResponse r = service.closeDay(request(new BigDecimal("40200"), "Small excess"));

        assertThat(r.variance()).isEqualByComparingTo("1000");
        assertThat(r.countersignStatus()).isEqualTo("NOT_REQUIRED");
    }

    @Test
    void closeDay_featureOff_locksClean() {
        when(orgRepo.findById(ORG)).thenReturn(Optional.of(org(null)));

        CashDayCloseResponse r = service.closeDay(request(new BigDecimal("50000"), "Huge, but nobody configured the gate"));

        assertThat(r.countersignStatus()).isEqualTo("NOT_REQUIRED");
    }

    @Test
    void countersign_confirmsPendingClose_withWitness() {
        CashDayClose pending = close("PENDING");
        when(cashDayCloseRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(pending));
        when(guard.requireAccountantForBranch(ORG, BRANCH)).thenReturn(accountant());

        CashDayCloseResponse r = service.countersign(500L);

        assertThat(r.countersignStatus()).isEqualTo("COUNTERSIGNED");
        assertThat(pending.getCountersignedBy()).isEqualTo(ACCOUNTANT_ID);
        assertThat(pending.getCountersignedAt()).isNotNull();
    }

    @Test
    void countersign_refusesOwnClose_makerChecker() {
        CashDayClose pending = close("PENDING");
        pending.setClosedBy(ACCOUNTANT_ID);
        when(cashDayCloseRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(pending));
        when(guard.requireAccountantForBranch(ORG, BRANCH)).thenReturn(accountant());

        assertThatThrownBy(() -> service.countersign(500L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("different person");
    }

    @Test
    void countersign_refusesWhenNothingPending() {
        CashDayClose clean = close("NOT_REQUIRED");
        when(cashDayCloseRepo.findByIdAndOrgId(500L, ORG)).thenReturn(Optional.of(clean));
        when(guard.requireAccountantForBranch(ORG, BRANCH)).thenReturn(accountant());

        assertThatThrownBy(() -> service.countersign(500L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("needs no countersign");
    }

    // ---------------------------------------------------------- fixtures

    private static Organization org(BigDecimal threshold) {
        Organization o = new Organization("JJ Motors (Demo)");
        ReflectionTestUtils.setField(o, "id", ORG);
        o.setCashVarianceCountersignThreshold(threshold);
        return o;
    }

    private static BigDecimal threshold(String value) {
        return new BigDecimal(value);
    }

    private static CloseDayRequest request(BigDecimal counted, String remark) {
        CloseDayRequest r = new CloseDayRequest();
        r.setCloseDate(DAY);
        r.setCountedAmount(counted);
        r.setVarianceRemark(remark);
        return r;
    }

    private static CashDayClose close(String countersignStatus) {
        CashDayClose c = new CashDayClose();
        ReflectionTestUtils.setField(c, "id", 500L);
        c.setOrgId(ORG);
        c.setBranchId(BRANCH);
        c.setCloseDate(DAY);
        c.setComputedClosing(new BigDecimal("39200"));
        c.setCountedAmount(new BigDecimal("42200"));
        c.setVariance(new BigDecimal("3000"));
        c.setClosedBy(CASHIER_ID);
        c.setCountersignStatus(countersignStatus);
        return c;
    }

    private static AppUser cashier() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", CASHIER_ID);
        u.setRole(Role.CASHIER);
        u.setHomeBranchId(BRANCH);
        return u;
    }

    private static AppUser accountant() {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", ACCOUNTANT_ID);
        u.setRole(Role.ACCOUNTANT);
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
