package com.dams.cash.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.springframework.stereotype.Component;

/**
 * Access rules for the Cash page:
 *
 *   - <b>writes</b> (movements, day-close) — CASHIER only, and always under their own home
 *     branch. There is no branch field on the write requests;
 *   - <b>the opening</b> — ACCOUNTANT only, for a branch within their access;
 *   - <b>reads</b> (drawer, movement list, close lookup) — the caller's branch scope:
 *     a CASHIER sees only their home branch, an ACCOUNTANT their assigned branches,
 *     OWNER / FINANCE_MANAGER any branch.
 */
@Component
public class CashPostingGuard {

    private final AppUserRepository userRepo;
    private final BranchRepository branchRepo;
    private final BranchScope branchScope;

    public CashPostingGuard(AppUserRepository userRepo, BranchRepository branchRepo, BranchScope branchScope) {
        this.userRepo = userRepo;
        this.branchRepo = branchRepo;
        this.branchScope = branchScope;
    }

    /** Assert the caller may create or change cash movements / close the day. Returns them. */
    public AppUser requireCashier(Long orgId) {
        AppUser me = me(orgId);
        if (me.getRole() != Role.CASHIER) {
            throw DamsException.forbidden("Only a cashier can record cash movements or close the day");
        }
        if (me.getHomeBranchId() == null) {
            throw DamsException.badRequest("Your account has no home branch — ask an Owner to set one");
        }
        return me;
    }

    /** Assert the caller is an Accountant who may set the opening for {@code branchId}. Returns them. */
    public AppUser requireAccountantForBranch(Long orgId, Long branchId) {
        AppUser me = me(orgId);
        if (me.getRole() != Role.ACCOUNTANT) {
            throw DamsException.forbidden("Only an accountant can set a branch's opening cash balance");
        }
        Branch branch = branchRepo.findByIdAndOrgId(branchId, orgId)
            .orElseThrow(() -> DamsException.notFound("Branch", branchId));
        if (!branchScope.canSeeBranch(branch.getId())) {
            throw DamsException.forbidden("You do not have access to branch '" + branch.getCode() + "'");
        }
        return me;
    }

    /**
     * Resolve which branch a read is for: a CASHIER is pinned to their home branch (the
     * {@code requestedBranchId} is ignored); everyone else must name a branch they can see.
     */
    public Long resolveViewBranch(Long orgId, Long requestedBranchId) {
        AppUser me = me(orgId);
        if (me.getRole() == Role.CASHIER) {
            if (me.getHomeBranchId() == null) {
                throw DamsException.badRequest("Your account has no home branch — ask an Owner to set one");
            }
            return me.getHomeBranchId();
        }
        if (requestedBranchId == null) {
            throw DamsException.badRequest("branchId is required");
        }
        Branch branch = branchRepo.findByIdAndOrgId(requestedBranchId, orgId)
            .orElseThrow(() -> DamsException.notFound("Branch", requestedBranchId));
        if (!branchScope.canSeeBranch(branch.getId())) {
            throw DamsException.forbidden("You do not have access to branch '" + branch.getCode() + "'");
        }
        return branch.getId();
    }

    private AppUser me(Long orgId) {
        return userRepo.findByIdAndOrganization_Id(branchScope.currentUserId(), orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));
    }
}
