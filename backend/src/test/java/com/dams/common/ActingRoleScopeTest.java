package com.dams.common;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.service.CashPostingGuard;
import com.dams.common.exception.DamsException;
import com.dams.common.security.ActingDetails;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.review.service.ReviewGuard;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserBranchAccess;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * "Acting as" scoping (plan.md rev 55): while a user is switched, branch scope and the posting
 * guards follow the ACTING role at the ONE branch picked — not the user's stored role/branches.
 * Real {@link BranchScope} and guards; only the repositories are mocked.
 */
@ExtendWith(MockitoExtension.class)
class ActingRoleScopeTest {

    private static final long ORG = 1L;
    private static final long USER = 7L;
    private static final long OOB = 2L;
    private static final long OOR = 3L;

    @Mock private AppUserRepository userRepo;
    @Mock private UserBranchAccessRepository branchAccessRepo;
    @Mock private OrganizationRepository orgRepo;
    @Mock private BranchRepository branchRepo;

    private BranchScope branchScope;
    private CashPostingGuard cashGuard;
    private ReviewGuard reviewGuard;

    @BeforeEach
    void setUp() {
        branchScope = new BranchScope(userRepo, branchAccessRepo, orgRepo);
        cashGuard = new CashPostingGuard(userRepo, branchRepo, branchScope);
        reviewGuard = new ReviewGuard(userRepo, branchScope);
        TenantContext.setOrgId(ORG);
        lenient().when(orgRepo.findById(ORG)).thenReturn(Optional.of(new Organization()));   // multi-branch toggle OFF
        lenient().when(branchRepo.findByIdAndOrgId(OOB, ORG)).thenReturn(Optional.of(branch(OOB, "OOB")));
        lenient().when(branchRepo.findByIdAndOrgId(OOR, ORG)).thenReturn(Optional.of(branch(OOR, "OOR")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // --- BranchScope ---

    @Test
    void anAccountantInTheirOwnRole_seesAllTheirAssignedBranches() {
        user(Role.ACCOUNTANT, null);
        lenient().when(branchAccessRepo.findByUserId(USER)).thenReturn(List.of(access(OOB), access(OOR)));
        actAs(Role.ACCOUNTANT, Role.ACCOUNTANT, null);

        assertThat(branchScope.allowedBranchIds()).contains(Set.of(OOB, OOR));
    }

    @Test
    void anAccountantActingAsCashierAtOOR_seesOnlyOOR() {
        user(Role.ACCOUNTANT, null);
        lenient().when(branchAccessRepo.findByUserId(USER)).thenReturn(List.of(access(OOB), access(OOR)));
        actAs(Role.CASHIER, Role.ACCOUNTANT, OOR);

        assertThat(branchScope.allowedBranchIds()).contains(Set.of(OOR));
        assertThat(branchScope.canSeeBranch(OOB)).isFalse();
    }

    @Test
    void anOwnerActingAsAccountantAtOOB_isNoLongerOrgWide() {
        user(Role.OWNER, null);
        actAs(Role.ACCOUNTANT, Role.OWNER, OOB);

        assertThat(branchScope.allowedBranchIds()).contains(Set.of(OOB));
        assertThat(branchScope.canSeeBranch(OOR)).isFalse();
    }

    @Test
    void anOwnerInTheirOwnRole_stillSeesEveryBranch() {
        user(Role.OWNER, null);
        actAs(Role.OWNER, Role.OWNER, null);

        assertThat(branchScope.allowedBranchIds()).isEmpty();   // empty Optional = no restriction
    }

    @Test
    void anAccountantActingAsFinanceManager_isOrgWide() {
        user(Role.ACCOUNTANT, null);
        actAs(Role.FINANCE_MANAGER, Role.ACCOUNTANT, OOR);

        assertThat(branchScope.allowedBranchIds()).isEmpty();
    }

    // --- Cash posting ---

    @Test
    void anAccountantActingAsCashier_mayRecordCash_atThePickedBranch() {
        AppUser me = user(Role.ACCOUNTANT, null);
        actAs(Role.CASHIER, Role.ACCOUNTANT, OOR);

        AppUser result = cashGuard.requireCashier(ORG);

        assertThat(result.getId()).isEqualTo(USER);   // still the real person — attribution is unchanged
        assertThat(ActingDetails.effectiveHomeBranch(me)).isEqualTo(OOR);
    }

    @Test
    void anAccountantNotActing_mayNotRecordCash() {
        user(Role.ACCOUNTANT, null);
        actAs(Role.ACCOUNTANT, Role.ACCOUNTANT, null);

        assertThatThrownBy(() -> cashGuard.requireCashier(ORG))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Only a cashier");
    }

    @Test
    void aCashierActingAsAccountant_mayNotRecordCash_evenWithAHomeBranch() {
        user(Role.CASHIER, OOB);
        actAs(Role.ACCOUNTANT, Role.CASHIER, OOB);

        assertThatThrownBy(() -> cashGuard.requireCashier(ORG))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Only a cashier");
    }

    @Test
    void anActingCashiersCashView_isPinnedToThePickedBranch_notTheRequestedOne() {
        user(Role.ACCOUNTANT, null);
        actAs(Role.CASHIER, Role.ACCOUNTANT, OOR);

        assertThat(cashGuard.resolveViewBranch(ORG, OOB)).isEqualTo(OOR);
    }

    @Test
    void aRealCashierNotActing_stillPostsToTheirHomeBranch() {
        AppUser me = user(Role.CASHIER, OOB);
        actAs(Role.CASHIER, Role.CASHIER, null);

        assertThat(cashGuard.requireCashier(ORG)).isSameAs(me);
        assertThat(ActingDetails.effectiveHomeBranch(me)).isEqualTo(OOB);
    }

    // --- Review ---

    @Test
    void aCashierActingAsAccountantAtOOR_canReviewAtOOR() {
        user(Role.CASHIER, OOB);
        actAs(Role.ACCOUNTANT, Role.CASHIER, OOR);

        assertThat(reviewGuard.requireAccountant().getId()).isEqualTo(USER);
    }

    @Test
    void aCashierNotActing_cannotReview() {
        user(Role.CASHIER, OOB);
        actAs(Role.CASHIER, Role.CASHIER, null);

        assertThatThrownBy(() -> reviewGuard.requireAccountant()).isInstanceOf(DamsException.class);
    }

    @Test
    void makerChecker_isPerPerson_soAjayCannotReviewAnEntryHeMadeWhileActingAsCashier() {
        AppUser me = user(Role.ACCOUNTANT, null);
        lenient().when(branchAccessRepo.findByUserId(USER)).thenReturn(List.of(access(OOR)));
        actAs(Role.ACCOUNTANT, Role.ACCOUNTANT, null);   // switched back to his own role

        // the entry was created (and last modified) by user 7 = Ajay, as a cashier
        assertThatThrownBy(() -> reviewGuard.requireCanReview(me, OOR, USER, USER, "OOR-R-1"))
            .isInstanceOf(DamsException.class);
    }

    // --- fixtures ---

    private AppUser user(Role role, Long homeBranchId) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", USER);
        u.setRole(role);
        u.setHomeBranchId(homeBranchId);
        lenient().when(userRepo.findByIdAndOrganization_Id(USER, ORG)).thenReturn(Optional.of(u));
        return u;
    }

    private static void actAs(Role acting, Role primary, Long branchId) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            USER, null, List.of(new SimpleGrantedAuthority(acting.name())));
        auth.setDetails(new ActingDetails(primary, branchId));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static UserBranchAccess access(long branchId) {
        UserBranchAccess a = new UserBranchAccess();
        ReflectionTestUtils.setField(a, "branchId", branchId);
        return a;
    }

    private static Branch branch(long id, String code) {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", id);
        b.setOrgId(ORG);
        b.setCode(code);
        b.setActive(true);
        return b;
    }
}
