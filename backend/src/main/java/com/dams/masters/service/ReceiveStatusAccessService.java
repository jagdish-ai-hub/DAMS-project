package com.dams.masters.service;

import com.dams.common.exception.DamsException;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveBusinessStatusRole;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRoleRepository;
import com.dams.user.entity.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who may set which job-card business status.
 *
 * The mapping is org data (Owner-editable in Masters), never a hard-coded list — the
 * dealership's split today is Cashier 3 / Accountant 4 / Finance 7, but that is their
 * process, not a rule of the system.
 *
 * Two callers: the Masters screen reads and rewrites the mapping, and JobCardService
 * enforces it on every write. The lists are small (a dozen rows per org), so reads load
 * the whole org's grants and filter in memory rather than issuing a query per status.
 */
@Service
public class ReceiveStatusAccessService {

    private static final Logger log = LoggerFactory.getLogger(ReceiveStatusAccessService.class);

    /**
     * The only roles that ever post a job card; OWNER is read-only on transactions.
     * Also the default for a newly added status — available to everyone until the Owner
     * narrows it, the same way V26 treated the statuses that predated role mapping.
     */
    public static final Set<Role> ASSIGNABLE_ROLES =
        Set.of(Role.CASHIER, Role.ACCOUNTANT, Role.FINANCE_MANAGER);

    private final ReceiveBusinessStatusRepository statusRepo;
    private final ReceiveBusinessStatusRoleRepository roleRepo;

    public ReceiveStatusAccessService(ReceiveBusinessStatusRepository statusRepo,
                                      ReceiveBusinessStatusRoleRepository roleRepo) {
        this.statusRepo = statusRepo;
        this.roleRepo = roleRepo;
    }

    /** Grants for the whole org, keyed by status id — for the Masters list response. */
    @Transactional(readOnly = true)
    public Map<Long, List<Role>> rolesByStatusId(Long orgId) {
        Map<Long, List<Role>> byStatus = new HashMap<>();
        for (ReceiveBusinessStatusRole grant : roleRepo.findByOrgId(orgId)) {
            byStatus.computeIfAbsent(grant.getStatusId(), k -> new ArrayList<>()).add(grant.getRole());
        }
        return byStatus;
    }

    @Transactional(readOnly = true)
    public List<Role> rolesFor(Long orgId, Long statusId) {
        List<Role> roles = new ArrayList<>();
        for (ReceiveBusinessStatusRole grant : roleRepo.findByOrgIdAndStatusId(orgId, statusId)) {
            roles.add(grant.getRole());
        }
        return roles;
    }

    /**
     * The active statuses {@code role} may choose from, in dropdown order.
     * An Owner gets nothing back, which is correct — they never set a status.
     */
    @Transactional(readOnly = true)
    public List<ReceiveBusinessStatus> selectableBy(Long orgId, Role role) {
        Map<Long, List<Role>> byStatus = rolesByStatusId(orgId);
        List<ReceiveBusinessStatus> allowed = new ArrayList<>();
        for (ReceiveBusinessStatus status : statusRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId)) {
            if (!status.isActive()) {
                continue;
            }
            List<Role> roles = byStatus.get(status.getId());
            if (roles != null && roles.contains(role)) {
                allowed.add(status);
            }
        }
        return allowed;
    }

    /**
     * Guards every write of job_card.business_status_id. Names the status and the role in
     * the error, because "forbidden" alone leaves a cashier with no idea why the save failed.
     */
    @Transactional(readOnly = true)
    public void requireMaySet(Long orgId, Role role, ReceiveBusinessStatus status) {
        if (!roleRepo.existsByOrgIdAndStatusIdAndRole(orgId, status.getId(), role)) {
            throw DamsException.forbidden(
                "Business status '" + status.getName() + "' cannot be set by a " + role + ".");
        }
    }

    /**
     * Rewrites one status's role grants wholesale (Owner, from Masters).
     *
     * An empty set is refused rather than saved: a status no role can set is invisible
     * everywhere and looks like data loss to whoever picks it up later. Deactivate the
     * status instead — that is what {@code active} is for.
     */
    @Transactional
    public void replaceRoles(Long orgId, Long statusId, Set<Role> roles) {
        Set<Role> cleaned = new LinkedHashSet<>();
        for (Role role : roles) {
            if (!ASSIGNABLE_ROLES.contains(role)) {
                throw DamsException.badRequest(
                    "A business status cannot be assigned to " + role + " — only "
                        + "CASHIER, ACCOUNTANT and FINANCE_MANAGER set statuses.");
            }
            cleaned.add(role);
        }
        if (cleaned.isEmpty()) {
            throw DamsException.badRequest(
                "A business status must be usable by at least one role. "
                    + "To retire it, deactivate it instead.");
        }

        roleRepo.deleteByOrgIdAndStatusId(orgId, statusId);
        for (Role role : cleaned) {
            ReceiveBusinessStatusRole grant = new ReceiveBusinessStatusRole();
            grant.setOrgId(orgId);
            grant.setStatusId(statusId);
            grant.setRole(role);
            roleRepo.save(grant);
        }
        log.info("Receive status roles replaced: orgId={} statusId={} roles={}", orgId, statusId, cleaned);
    }
}
