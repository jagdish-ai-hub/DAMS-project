package com.dams.receive.service;

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

import java.math.BigDecimal;

/**
 * One place for the rule: <b>a cashier records payments only against job cards in their own
 * home branch.</b>
 *
 * The {@code multi_branch_cashier_access} toggle widens what a cashier can search and view —
 * it never changes which branch their documents post under (plan.md locked decisions;
 * AGENT.md #2). So a cashier may see a cross-branch job card in search results, but Add
 * Payment / New Receipt against it is blocked: DAMS will not guess which branch's cash
 * ledger the money belongs to. That routing is still an open question for the business
 * owners (TBD in AGENT.md).
 */
@Component
public class ReceivePaymentGuard {

    private final AppUserRepository userRepo;
    private final BranchRepository branchRepo;
    private final BranchScope branchScope;

    public ReceivePaymentGuard(AppUserRepository userRepo,
                               BranchRepository branchRepo,
                               BranchScope branchScope) {
        this.userRepo = userRepo;
        this.branchRepo = branchRepo;
        this.branchScope = branchScope;
    }

    /**
     * Assert the signed-in user may create or modify a receive document for this job card.
     * Returns the caller's {@link AppUser} for the id trail.
     */
    public AppUser requireCanPost(Long orgId, JobCard jobCard) {
        AppUser me = userRepo.findByIdAndOrganization_Id(branchScope.currentUserId(), orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));

        if (me.getRole() != Role.CASHIER) {
            throw DamsException.forbidden("Only a cashier can create or change receive documents");
        }
        if (me.getHomeBranchId() == null) {
            throw DamsException.badRequest("Your account has no home branch — ask an Owner to set one");
        }
        if (!jobCard.getBranchId().equals(me.getHomeBranchId())) {
            throw DamsException.conflict(
                "Job card " + reference(orgId, jobCard) + " belongs to " + branchCode(orgId, jobCard.getBranchId())
                    + ". You can only record payments for job cards in your home branch ("
                    + branchCode(orgId, me.getHomeBranchId())
                    + "). Cross-branch payment routing is not defined yet.");
        }
        return me;
    }

    /**
     * UI hint (no throw): may the signed-in user press "Add Payment" on this job card right
     * now? True only for a cashier, in their own branch, with an open balance and no closed
     * claim.
     */
    public boolean canRecordPayment(Long orgId, JobCard jobCard, BigDecimal pendingAmount, boolean claimClosed) {
        if (branchScope.currentRole() != Role.CASHIER || claimClosed || pendingAmount.signum() <= 0) {
            return false;
        }
        return userRepo.findByIdAndOrganization_Id(branchScope.currentUserId(), orgId)
            .map(me -> jobCard.getBranchId().equals(me.getHomeBranchId()))
            .orElse(false);
    }

    private String reference(Long orgId, JobCard jobCard) {
        return JobCardResponse.reference(branchCode(orgId, jobCard.getBranchId()), jobCard.getId());
    }

    private String branchCode(Long orgId, Long branchId) {
        return branchRepo.findByIdAndOrgId(branchId, orgId).map(Branch::getCode).orElse("?");
    }
}
