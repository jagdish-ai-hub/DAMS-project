package com.dams.expense.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.entity.JobCard;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.springframework.stereotype.Component;

/**
 * The expense-side twin of {@link com.dams.receive.service.ReceivePaymentGuard}: <b>a
 * cashier records expenses only under their own home branch.</b> An expense may have no job
 * card (branch overhead); when it does, that job card must be in the cashier's home branch
 * too — DAMS will not guess which branch's cash an expense on a cross-branch job card
 * belongs to. The {@code multi_branch_cashier_access} toggle widens search / view only, not
 * where documents post.
 */
@Component
public class ExpensePostingGuard {

    private final AppUserRepository userRepo;
    private final BranchRepository branchRepo;
    private final BranchScope branchScope;

    public ExpensePostingGuard(AppUserRepository userRepo,
                               BranchRepository branchRepo,
                               BranchScope branchScope) {
        this.userRepo = userRepo;
        this.branchRepo = branchRepo;
        this.branchScope = branchScope;
    }

    /**
     * Assert the signed-in user may create or modify an expense document (optionally tagged
     * to {@code jobCard}). Returns the caller's {@link AppUser} for the id trail.
     */
    public AppUser requireCanPost(Long orgId, JobCard jobCard) {
        AppUser me = userRepo.findByIdAndOrganization_Id(branchScope.currentUserId(), orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));

        if (me.getRole() != Role.CASHIER) {
            throw DamsException.forbidden("Only a cashier can create or change expense documents");
        }
        if (me.getHomeBranchId() == null) {
            throw DamsException.badRequest("Your account has no home branch — ask an Owner to set one");
        }
        if (jobCard != null && !jobCard.getBranchId().equals(me.getHomeBranchId())) {
            throw DamsException.conflict(
                "Job card " + reference(orgId, jobCard) + " belongs to " + branchCode(orgId, jobCard.getBranchId())
                    + ". You can only record expenses for job cards in your home branch ("
                    + branchCode(orgId, me.getHomeBranchId())
                    + "). Cross-branch expense routing is not defined yet.");
        }
        return me;
    }

    private String reference(Long orgId, JobCard jobCard) {
        return JobCardResponse.reference(branchCode(orgId, jobCard.getBranchId()), jobCard.getId());
    }

    private String branchCode(Long orgId, Long branchId) {
        return branchRepo.findByIdAndOrgId(branchId, orgId).map(Branch::getCode).orElse("?");
    }
}
