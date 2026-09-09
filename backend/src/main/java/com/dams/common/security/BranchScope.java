package com.dams.common.security;

import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserBranchAccess;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves which branches the signed-in user may see, for reads that span branches
 * (universal search, and later the review queues / dashboard):
 *
 *   OWNER / FINANCE_MANAGER  — every branch in the org (no restriction)
 *   ACCOUNTANT               — exactly their user_branch_access branches
 *   CASHIER                  — their home branch only, UNLESS the org's
 *                              multi_branch_cashier_access toggle is ON, then org-wide
 *
 * The toggle never changes which branch a cashier's own documents post under — only what
 * they can search and see. See AGENT.md / plan.md locked decisions.
 *
 * Reads the user and toggle fresh from the DB (not the JWT) so a change to an accountant's
 * branch access or the org toggle takes effect without waiting for the ~8h token to expire.
 */
@Component
public class BranchScope {

    private final AppUserRepository userRepo;
    private final UserBranchAccessRepository branchAccessRepo;
    private final OrganizationRepository orgRepo;

    public BranchScope(AppUserRepository userRepo,
                       UserBranchAccessRepository branchAccessRepo,
                       OrganizationRepository orgRepo) {
        this.userRepo = userRepo;
        this.branchAccessRepo = branchAccessRepo;
        this.orgRepo = orgRepo;
    }

    /**
     * @return the branch ids the caller may see, or {@link Optional#empty()} when the
     *         caller is not branch-restricted (see everything in the org). An empty set
     *         (present) means "restricted to nothing" — e.g. a cashier with no home branch.
     */
    public Optional<Set<Long>> allowedBranchIds() {
        Long orgId = TenantContext.requireOrgId();
        AppUser user = userRepo.findByIdAndOrganization_Id(currentUserId(), orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));

        return switch (user.getRole()) {
            case OWNER, FINANCE_MANAGER -> Optional.empty();
            case ACCOUNTANT -> Optional.of(
                branchAccessRepo.findByUserId(user.getId()).stream()
                    .map(UserBranchAccess::getBranchId)
                    .collect(Collectors.toSet()));
            case CASHIER -> {
                Organization org = orgRepo.findById(orgId)
                    .orElseThrow(() -> DamsException.notFound("Organization", orgId));
                if (org.isMultiBranchCashierAccess()) {
                    yield Optional.empty();
                }
                yield Optional.of(user.getHomeBranchId() == null
                    ? Set.of()
                    : Set.of(user.getHomeBranchId()));
            }
            case SUPER_ADMIN -> Optional.of(Set.of()); // no org context — should never reach here
        };
    }

    /** True when {@code branchId} is within the caller's allowed set. */
    public boolean canSeeBranch(Long branchId) {
        return allowedBranchIds().map(set -> set.contains(branchId)).orElse(true);
    }

    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw DamsException.forbidden("No authenticated user in context");
        }
        return userId;
    }

    public Role currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().isEmpty()) {
            throw DamsException.forbidden("No authenticated user in context");
        }
        return Role.valueOf(auth.getAuthorities().iterator().next().getAuthority());
    }
}
