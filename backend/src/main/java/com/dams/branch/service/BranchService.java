package com.dams.branch.service;

import com.dams.branch.dto.BranchRequest;
import com.dams.branch.dto.BranchResponse;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Branch CRUD, always scoped to the caller's org (TenantContext). Branches are
 * deactivated, never deleted, so historical documents keep resolving.
 */
@Service
public class BranchService {

    private static final Logger log = LoggerFactory.getLogger(BranchService.class);

    private final BranchRepository branchRepo;
    private final AppUserRepository userRepo;
    private final UserBranchAccessRepository branchAccessRepo;

    public BranchService(BranchRepository branchRepo,
                         AppUserRepository userRepo,
                         UserBranchAccessRepository branchAccessRepo) {
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.branchAccessRepo = branchAccessRepo;
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> list() {
        Long orgId = TenantContext.requireOrgId();
        return branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .map(b -> BranchResponse.of(b, assignedUserCount(b.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public BranchResponse get(Long id) {
        Branch branch = load(id);
        return BranchResponse.of(branch, assignedUserCount(branch.getId()));
    }

    @Transactional
    public BranchResponse create(BranchRequest request) {
        Long orgId = TenantContext.requireOrgId();
        String code = normaliseCode(request.getCode());

        if (branchRepo.existsByOrgIdAndCodeIgnoreCase(orgId, code)) {
            throw DamsException.conflict("Branch code '" + code + "' is already used in this organization");
        }

        Branch branch = new Branch();
        branch.setOrgId(orgId);
        branch.setName(request.getName().trim());
        branch.setCode(code);
        branch.setActive(true);
        branch = branchRepo.save(branch);

        log.info("Branch created: orgId={} branchId={} code={}", orgId, branch.getId(), code);
        return BranchResponse.of(branch, 0);
    }

    @Transactional
    public BranchResponse update(Long id, BranchRequest request) {
        Long orgId = TenantContext.requireOrgId();
        Branch branch = load(id);

        String code = normaliseCode(request.getCode());
        if (!code.equalsIgnoreCase(branch.getCode())
                && branchRepo.existsByOrgIdAndCodeIgnoreCase(orgId, code)) {
            throw DamsException.conflict("Branch code '" + code + "' is already used in this organization");
        }

        branch.setName(request.getName().trim());
        branch.setCode(code);
        if (request.getActive() != null) {
            branch.setActive(request.getActive());
        }
        branch = branchRepo.save(branch);

        log.info("Branch updated: orgId={} branchId={} code={} active={}",
            orgId, branch.getId(), branch.getCode(), branch.isActive());
        return BranchResponse.of(branch, assignedUserCount(branch.getId()));
    }

    // --- helpers ---

    private Branch load(Long id) {
        Long orgId = TenantContext.requireOrgId();
        return branchRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Branch", id));
    }

    private long assignedUserCount(Long branchId) {
        // A user is "assigned" to a branch if it's their home branch (cashier) or they
        // have an access row (accountant).
        return userRepo.countByHomeBranchId(branchId) + branchAccessRepo.countByBranchId(branchId);
    }

    private String normaliseCode(String raw) {
        return raw == null ? null : raw.trim().toUpperCase();
    }
}
