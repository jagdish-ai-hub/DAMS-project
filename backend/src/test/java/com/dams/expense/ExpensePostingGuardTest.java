package com.dams.expense;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.expense.service.ExpensePostingGuard;
import com.dams.jobcard.entity.JobCard;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * "A cashier records expenses only under their own home branch" — and, when the expense is
 * tagged to a job card, that job card must be in the same branch.
 */
@ExtendWith(MockitoExtension.class)
class ExpensePostingGuardTest {

    private static final long ORG = 1L;
    private static final long CASHIER_ID = 7L;
    private static final long HOME_BRANCH = 3L;   // OOR
    private static final long OTHER_BRANCH = 2L;  // OOB

    @Mock private AppUserRepository userRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private BranchScope branchScope;

    private ExpensePostingGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ExpensePostingGuard(userRepo, branchRepo, branchScope);
        lenient().when(branchScope.currentUserId()).thenReturn(CASHIER_ID);
        lenient().when(branchRepo.findByIdAndOrgId(eq(HOME_BRANCH), eq(ORG)))
            .thenReturn(Optional.of(branch(HOME_BRANCH, "OOR")));
        lenient().when(branchRepo.findByIdAndOrgId(eq(OTHER_BRANCH), eq(ORG)))
            .thenReturn(Optional.of(branch(OTHER_BRANCH, "OOB")));
        lenient().when(userRepo.findByIdAndOrganization_Id(CASHIER_ID, ORG))
            .thenReturn(Optional.of(cashier(HOME_BRANCH)));
    }

    @Test
    void requireCanPost_allowsCashier_forABranchOverheadExpense_withNoJobCard() {
        AppUser me = guard.requireCanPost(ORG, null);
        assertThat(me.getId()).isEqualTo(CASHIER_ID);
    }

    @Test
    void requireCanPost_allowsCashier_onAJobCardInTheirOwnBranch() {
        AppUser me = guard.requireCanPost(ORG, jobCard(HOME_BRANCH));
        assertThat(me.getHomeBranchId()).isEqualTo(HOME_BRANCH);
    }

    @Test
    void requireCanPost_blocksCashier_onACrossBranchJobCard() {
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

        assertThatThrownBy(() -> guard.requireCanPost(ORG, null))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Only a cashier");
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
