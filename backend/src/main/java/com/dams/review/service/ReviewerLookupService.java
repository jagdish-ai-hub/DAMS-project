package com.dams.review.service;

import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.review.dto.ReviewerResponse;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserRoleGrant;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import com.dams.user.repository.UserRoleGrantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Who can clear an entry that maker-checker blocks the caller from reviewing (plan.md rev 59) —
 * so the review screen can say "Anita Rao or another accountant at OOJ can verify this" instead
 * of leaving a dead end. Names only; no email, no role detail beyond the step asked about.
 *
 * Eligible = an active user in the org who can act in {@code step} at the branch, either by
 * their own role (the role needs a branch-access row) or by
 * an Owner-granted extra role. Owners are not listed: they can act as anyone, but "ask the
 * Owner" is not a useful hint.
 */
@Service
public class ReviewerLookupService {

    private final AppUserRepository userRepo;
    private final UserBranchAccessRepository branchAccessRepo;
    private final UserRoleGrantRepository grantRepo;
    private final BranchScope branchScope;

    public ReviewerLookupService(AppUserRepository userRepo,
                                 UserBranchAccessRepository branchAccessRepo,
                                 UserRoleGrantRepository grantRepo,
                                 BranchScope branchScope) {
        this.userRepo = userRepo;
        this.branchAccessRepo = branchAccessRepo;
        this.grantRepo = grantRepo;
        this.branchScope = branchScope;
    }

    /**
     * @param step    ACCOUNTANT (verify step) or FINANCE_MANAGER (approve step)
     * @param exclude user ids that cannot clear this entry — its creator and last modifier
     */
    @Transactional(readOnly = true)
    public List<ReviewerResponse> reviewersFor(Long branchId, Role step, Collection<Long> exclude) {
        if (step != Role.ACCOUNTANT && step != Role.FINANCE_MANAGER) {
            throw DamsException.badRequest("step must be ACCOUNTANT or FINANCE_MANAGER");
        }
        if (branchId == null) {
            throw DamsException.badRequest("branchId is required");
        }
        if (!branchScope.canSeeBranch(branchId)) {
            throw DamsException.forbidden("You do not have access to that branch");
        }
        Long orgId = TenantContext.requireOrgId();

        // Users who hold the step through an Owner-granted extra role.
        Set<Long> viaGrant = new HashSet<>();
        List<UserRoleGrant> grants = grantRepo.findByRoleAndBranchId(step, branchId);
        grants.forEach(g -> viaGrant.add(g.getUserId()));

        Set<Long> usersAssignedAtBranch = new HashSet<>();
        branchAccessRepo.findByBranchId(branchId).stream()
            .map(a -> a.getId().getUserId())
            .forEach(usersAssignedAtBranch::add);

        Set<Long> excluded = exclude == null ? Set.of() : new HashSet<>(exclude);
        return userRepo.findByOrganization_Id(orgId).stream()
            .filter(AppUser::isActive)
            .filter(u -> !excluded.contains(u.getId()))
            .filter(u -> u.getRole() != Role.OWNER && u.getRole() != Role.SUPER_ADMIN)
            .filter(u -> viaGrant.contains(u.getId()) || holdsStepByOwnRole(u, step, usersAssignedAtBranch))
            .sorted(Comparator.comparing(AppUser::getName, String.CASE_INSENSITIVE_ORDER))
            .map(u -> new ReviewerResponse(u.getId(), u.getName()))
            .toList();
    }

    private static boolean holdsStepByOwnRole(AppUser u, Role step, Set<Long> usersAssignedAtBranch) {
        if (u.getRole() != step) {
            return false;
        }
        return usersAssignedAtBranch.contains(u.getId());
    }
}
