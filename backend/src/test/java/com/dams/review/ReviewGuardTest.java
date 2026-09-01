package com.dams.review;

import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.review.service.ReviewGuard;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The review access rules: ACCOUNTANT only, branch in scope, and never a document the
 * caller created or last modified (maker-checker, AGENT.md).
 */
@ExtendWith(MockitoExtension.class)
class ReviewGuardTest {

    private static final long ORG = 1L;
    private static final long ACCOUNTANT_ID = 50L;
    private static final long BRANCH = 3L;

    @Mock private AppUserRepository userRepo;
    @Mock private BranchScope branchScope;

    private ReviewGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ReviewGuard(userRepo, branchScope);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.currentUserId()).thenReturn(ACCOUNTANT_ID);
        lenient().when(userRepo.findByIdAndOrganization_Id(ACCOUNTANT_ID, ORG))
            .thenReturn(Optional.of(user(ACCOUNTANT_ID, Role.ACCOUNTANT)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requireAccountant_returnsTheUser_whenRoleIsAccountant() {
        AppUser me = guard.requireAccountant();
        assertThat(me.getId()).isEqualTo(ACCOUNTANT_ID);
    }

    @Test
    void requireAccountant_forbidden_whenRoleIsNotAccountant() {
        when(userRepo.findByIdAndOrganization_Id(ACCOUNTANT_ID, ORG))
            .thenReturn(Optional.of(user(ACCOUNTANT_ID, Role.CASHIER)));
        assertThatThrownBy(() -> guard.requireAccountant())
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Only an accountant");
    }

    @Test
    void requireFinanceManager_returnsTheUser_whenRoleIsFinanceManager() {
        when(userRepo.findByIdAndOrganization_Id(ACCOUNTANT_ID, ORG))
            .thenReturn(Optional.of(user(ACCOUNTANT_ID, Role.FINANCE_MANAGER)));
        assertThat(guard.requireFinanceManager().getId()).isEqualTo(ACCOUNTANT_ID);
    }

    @Test
    void requireFinanceManager_forbidden_whenRoleIsAccountant() {
        assertThatThrownBy(() -> guard.requireFinanceManager())
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("Only a Finance Manager");
    }

    @Test
    void requireCanReview_forbidden_whenBranchNotInScope() {
        when(branchScope.canSeeBranch(BRANCH)).thenReturn(false);
        AppUser me = user(ACCOUNTANT_ID, Role.ACCOUNTANT);
        assertThatThrownBy(() -> guard.requireCanReview(me, BRANCH, 7L, 7L, "OOR-AUG26-R-001"))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("not assigned to the branch");
    }

    @Test
    void requireCanReview_conflict_whenCallerCreatedTheDocument() {
        when(branchScope.canSeeBranch(BRANCH)).thenReturn(true);
        AppUser me = user(ACCOUNTANT_ID, Role.ACCOUNTANT);
        assertThatThrownBy(() -> guard.requireCanReview(me, BRANCH, ACCOUNTANT_ID, 7L, "OOR-AUG26-R-001"))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("created or last modified it");
    }

    @Test
    void requireCanReview_conflict_whenCallerLastModifiedTheDocument() {
        when(branchScope.canSeeBranch(BRANCH)).thenReturn(true);
        AppUser me = user(ACCOUNTANT_ID, Role.ACCOUNTANT);
        assertThatThrownBy(() -> guard.requireCanReview(me, BRANCH, 7L, ACCOUNTANT_ID, "OOR-AUG26-R-001"))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("created or last modified it");
    }

    @Test
    void requireCanReview_passes_whenThirdPartyAndBranchInScope() {
        when(branchScope.canSeeBranch(BRANCH)).thenReturn(true);
        AppUser me = user(ACCOUNTANT_ID, Role.ACCOUNTANT);
        guard.requireCanReview(me, BRANCH, 7L, 8L, "OOR-AUG26-R-001"); // no throw
    }

    private static AppUser user(long id, Role role) {
        AppUser u = new AppUser();
        ReflectionTestUtils.setField(u, "id", id);
        u.setRole(role);
        return u;
    }
}
