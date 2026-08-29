package com.dams.receive;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.jobcard.entity.JobCard;
import com.dams.receive.service.ReceivePaymentGuard;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The rule "a cashier records payments only against job cards in their own home branch" —
 * unchanged by the multi_branch_cashier_access toggle (that only widens search/visibility).
 */
@ExtendWith(MockitoExtension.class)
class ReceivePaymentGuardTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
    private static final long HOME_BRANCH = 3L;   // OOR
    private static final long OTHER_BRANCH = 2L;  // OOB

    @Mock private AppUserRepository userRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private BranchScope branchScope;

    private ReceivePaymentGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ReceivePaymentGuard(userRepo, branchRepo, branchScope);
        lenient().when(branchScope.currentUserId()).thenReturn(CASHIER_ID);
        lenient().when(branchScope.currentRole()).thenReturn(Role.CASHIER);
        lenient().when(branchRepo.findByIdAndOrgId(eq(HOME_BRANCH), eq(ORG)))
            .thenReturn(Optional.of(branch(HOME_BRANCH, "OOR")));
        lenient().when(branchRepo.findByIdAndOrgId(eq(OTHER_BRANCH), eq(ORG)))
            .thenReturn(Optional.of(branch(OTHER_BRANCH, "OOB")));
    }

    @Test
    void requireCanPost_allowsCashier_inTheirOwnBranch() {
        when(userRepo.findByIdAndOrganization_Id(CASHIER_ID, ORG)).thenReturn(Optional.of(cashier(HOME_BRANCH)));
        AppUser me = guard.requireCanPost(ORG, jobCard(HOME_BRANCH));
        assertThat(me.getId()).isEqualTo(CASHIER_ID);
    }

    @Test
    void requireCanPost_blocksCashier_onACrossBranchJobCard_evenWhenVisibleViaSearch() {
        when(userRepo.findByIdAndOrganization_Id(CASHIER_ID, ORG)).thenReturn(Optional.of(cashier(HOME_BRANCH)));

        assertThatThrownBy(() -> guard.requireCanPost(ORG, jobCard(OTHER_BRANCH)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("home branch")
            .hasMessageContaining("OOB")
            .hasMessageContaining("OOR");
    }

    @Test
    void requireCanPost_blocksNonCashierRoles() {
        AppUser accountant = cashier(HOME_BRANCH);
        accountant.setRole(Role.ACCOUNTANT);
        when(userRepo.findByIdAndOrganization_Id(CASHIER_ID, ORG)).thenReturn(Optional.of(accountant));

        assertThatThrownBy(() -> guard.requireCanPost(ORG, jobCard(HOME_BRANCH)))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Only a cashier");
    }

    @Test
    void canRecordPayment_trueOnlyForOwnBranchWithOpenBalance() {
        when(userRepo.findByIdAndOrganization_Id(CASHIER_ID, ORG)).thenReturn(Optional.of(cashier(HOME_BRANCH)));

        assertThat(guard.canRecordPayment(ORG, jobCard(HOME_BRANCH), new BigDecimal("500"), false)).isTrue();
        assertThat(guard.canRecordPayment(ORG, jobCard(HOME_BRANCH), BigDecimal.ZERO, false)).isFalse();
        assertThat(guard.canRecordPayment(ORG, jobCard(HOME_BRANCH), new BigDecimal("500"), true)).isFalse();
        assertThat(guard.canRecordPayment(ORG, jobCard(OTHER_BRANCH), new BigDecimal("500"), false)).isFalse();
    }

    private static AppUser cashier(long homeBranchId) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", CASHIER_ID);
        u.setRole(Role.CASHIER);
        u.setHomeBranchId(homeBranchId);
        return u;
    }

    private static JobCard jobCard(long branchId) {
        JobCard jc = new JobCard();
        ReflectionTestUtils.setField(jc, "id", 60L);
        jc.setOrgId(ORG);
        jc.setBranchId(branchId);
        return jc;
    }

    private static Branch branch(long id, String code) {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", id);
        b.setOrgId(ORG);
        b.setCode(code);
        b.setName(code + " branch");
        b.setActive(true);
        return b;
    }
}
