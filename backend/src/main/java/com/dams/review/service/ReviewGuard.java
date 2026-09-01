package com.dams.review.service;

import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.springframework.stereotype.Component;

/**
 * Access rules for the review actions — the Accountant step (verify / query / reject /
 * override / close-expense) and the Finance Manager step (approve / query / reject):
 *
 *   - the caller must hold the right role (ACCOUNTANT, or FINANCE_MANAGER for approval);
 *   - the document must sit in a branch the caller can see ({@link BranchScope} — an
 *     accountant's assigned branches; FM / Owner are org-wide so this never restricts them);
 *   - maker-checker (AGENT.md): a user never reviews or approves an entry they created or
 *     last modified — someone else must.
 *
 * "Last modified" tracks the cashier's last touch (create / submit / resubmit / line edit).
 * An accountant's own amount override deliberately does NOT bump it — the override trail is
 * its own columns plus an OVERRIDE audit row — so one accountant may override a line and
 * still verify the same document (matches review-close.html).
 */
@Component
public class ReviewGuard {

    private final AppUserRepository userRepo;
    private final BranchScope branchScope;

    public ReviewGuard(AppUserRepository userRepo, BranchScope branchScope) {
        this.userRepo = userRepo;
        this.branchScope = branchScope;
    }

    /** Assert the caller is an Accountant. Returns them. */
    public AppUser requireAccountant() {
        AppUser me = me();
        if (me.getRole() != Role.ACCOUNTANT) {
            throw DamsException.forbidden("Only an accountant can review submitted entries");
        }
        return me;
    }

    /** Assert the caller is a Finance Manager. Returns them. */
    public AppUser requireFinanceManager() {
        AppUser me = me();
        if (me.getRole() != Role.FINANCE_MANAGER) {
            throw DamsException.forbidden("Only a Finance Manager can approve or close claims");
        }
        return me;
    }

    private AppUser me() {
        return userRepo.findByIdAndOrganization_Id(branchScope.currentUserId(), TenantContext.requireOrgId())
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));
    }

    /**
     * Assert this accountant may act on this specific document: branch in scope, and neither
     * its creator nor its last modifier.
     *
     * @param docLabel the document number (or {@code #id}) for the error message
     */
    public void requireCanReview(AppUser me, Long branchId, Long createdBy, Long lastModifiedBy, String docLabel) {
        if (!branchScope.canSeeBranch(branchId)) {
            throw DamsException.forbidden("You are not assigned to the branch of document " + docLabel);
        }
        if (me.getId().equals(createdBy) || me.getId().equals(lastModifiedBy)) {
            throw DamsException.conflict("You cannot review document " + docLabel
                + " because you created or last modified it — a different accountant must.");
        }
    }
}
