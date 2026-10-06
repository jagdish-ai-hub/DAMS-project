package com.dams.review;

import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.review.dto.ReviewerResponse;
import com.dams.review.service.ReviewerLookupService;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserBranchAccess;
import com.dams.user.entity.UserRoleGrant;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import com.dams.user.repository.UserRoleGrantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

/**
 * "Who can clear this blocked entry" (plan.md rev 59). Mirrors the live OOJ case: the Owner
 * entered the entries as Cashier, so the people who can verify them are the other accountants
 * at OOJ, and the people who can approve are the Finance Managers.
 */
@ExtendWith(MockitoExtension.class)
class ReviewerLookupServiceTest {

    private static final long ORG = 1L;
    private static final long OOJ = 1L;
    private static final long OOR = 3L;
    private static final long PRIYA = 2L;      // Owner — the maker
    private static final long RAKESH = 3L;     // Finance Manager, org-wide
    private static final long ANITA = 4L;      // Accountant at OOB, OOJ, OOR
    private static final long DEEPA = 5L;      // Accountant at OOR only
    private static final long ASHA = 6L;       // Cashier granted Accountant at OOJ

    @Mock private AppUserRepository userRepo;
    @Mock private UserBranchAccessRepository branchAccessRepo;
    @Mock private UserRoleGrantRepository grantRepo;
    @Mock private BranchScope branchScope;

    private ReviewerLookupService service;

    @BeforeEach
    void setUp() {
        service = new ReviewerLookupService(userRepo, branchAccessRepo, grantRepo, branchScope);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.canSeeBranch(OOJ)).thenReturn(true);
        lenient().when(userRepo.findByOrganization_Id(ORG)).thenReturn(List.of(
            user(PRIYA, "Priya Nair", Role.OWNER, true),
            user(RAKESH, "Rakesh Menon", Role.FINANCE_MANAGER, true),
            user(ANITA, "Anita Rao", Role.ACCOUNTANT, true),
            user(DEEPA, "Deepa Iyer", Role.ACCOUNTANT, true),
            user(ASHA, "Asha Das", Role.CASHIER, true)));
        lenient().when(branchAccessRepo.findByBranchId(OOJ)).thenReturn(List.of(access(ANITA)));
        lenient().when(grantRepo.findByRoleAndBranchId(Role.ACCOUNTANT, OOJ)).thenReturn(List.of());
        lenient().when(grantRepo.findByRoleAndBranchIdIsNull(Role.FINANCE_MANAGER)).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void accountantStep_listsOnlyAccountantsAssignedToThatBranch() {
        List<ReviewerResponse> out = service.reviewersFor(OOJ, Role.ACCOUNTANT, List.of(PRIYA));

        assertThat(out).extracting(ReviewerResponse::name).containsExactly("Anita Rao");   // not Deepa (OOR only)
    }

    @Test
    void accountantStep_alsoListsSomeoneGrantedAccountantAtThatBranch() {
        lenient().when(grantRepo.findByRoleAndBranchId(Role.ACCOUNTANT, OOJ)).thenReturn(List.of(grant(ASHA)));

        List<ReviewerResponse> out = service.reviewersFor(OOJ, Role.ACCOUNTANT, List.of(PRIYA));

        assertThat(out).extracting(ReviewerResponse::name).containsExactly("Anita Rao", "Asha Das");
    }

    @Test
    void financeStep_listsFinanceManagers_andAnyoneGrantedTheOrgWideRole() {
        lenient().when(grantRepo.findByRoleAndBranchIdIsNull(Role.FINANCE_MANAGER)).thenReturn(List.of(grant(ASHA)));

        List<ReviewerResponse> out = service.reviewersFor(OOJ, Role.FINANCE_MANAGER, List.of(PRIYA));

        assertThat(out).extracting(ReviewerResponse::name).containsExactly("Asha Das", "Rakesh Menon");
    }

    @Test
    void theMakerAndTheLastModifierAreNeverListed() {
        List<ReviewerResponse> out = service.reviewersFor(OOJ, Role.ACCOUNTANT, List.of(ANITA, PRIYA));

        assertThat(out).isEmpty();
    }

    @Test
    void ownersAreNeverListed_evenWhenNotExcluded() {
        List<ReviewerResponse> out = service.reviewersFor(OOJ, Role.FINANCE_MANAGER, List.of());

        assertThat(out).extracting(ReviewerResponse::name).containsExactly("Rakesh Menon");
    }

    @Test
    void inactiveUsersAreNeverListed() {
        lenient().when(userRepo.findByOrganization_Id(ORG)).thenReturn(List.of(
            user(RAKESH, "Rakesh Menon", Role.FINANCE_MANAGER, false)));

        assertThat(service.reviewersFor(OOJ, Role.FINANCE_MANAGER, List.of())).isEmpty();
    }

    @Test
    void aBranchTheCallerCannotSee_isForbidden() {
        lenient().when(branchScope.canSeeBranch(OOR)).thenReturn(false);

        assertThatThrownBy(() -> service.reviewersFor(OOR, Role.ACCOUNTANT, List.of()))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("access");
    }

    @Test
    void onlyTheTwoReviewStepsAreAccepted() {
        assertThatThrownBy(() -> service.reviewersFor(OOJ, Role.CASHIER, List.of()))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("step must be");
        assertThatThrownBy(() -> service.reviewersFor(null, Role.ACCOUNTANT, List.of()))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("branchId");
    }

    // --- fixtures ---

    private static AppUser user(long id, String name, Role role, boolean active) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", id);
        u.setName(name);
        u.setRole(role);
        u.setActive(active);
        return u;
    }

    private static UserBranchAccess access(long userId) {
        UserBranchAccess a = new UserBranchAccess();
        a.setId(new UserBranchAccess.UserBranchAccessId(userId, OOJ));
        return a;
    }

    private static UserRoleGrant grant(long userId) {
        UserRoleGrant g = new UserRoleGrant();
        g.setOrgId(ORG);
        g.setUserId(userId);
        return g;
    }
}
