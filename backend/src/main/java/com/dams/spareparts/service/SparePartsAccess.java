package com.dams.spareparts.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.ActingDetails;
import com.dams.config.TenantContext;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.spareparts.dto.SparePartsDtos.AccessInfo;
import com.dams.spareparts.dto.SparePartsDtos.BranchInfo;
import com.dams.spareparts.entity.SparePermission;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserBranchAccess;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The one gate every Spare Parts action goes through. It answers three questions, always from the
 * database (never from the token alone) so a change by the Owner takes effect on the next click:
 *
 *   1. Is the module switched on for this organization?              (else 403)
 *   2. May this role look at stock? Owner, Inventory Manager, Store Keeper. (else 403)
 *   3. For an action: does the role's grid allow it, and is it at one of the caller's own branches?
 *
 * Looking is org-wide (the point of the module is "search here first"); acting is per branch. The Owner
 * is view-only: to act they use Switch role, which makes them an Inventory Manager at ONE branch.
 */
@Component
public class SparePartsAccess {

    private static final Set<Role> VIEWERS = EnumSet.of(Role.OWNER, Role.INVENTORY_MANAGER, Role.STORE_KEEPER);

    /** Who is calling, resolved once per request. {@code actBranchIds} is empty for the Owner. */
    public record Caller(Long userId, Long orgId, Role role, Set<Long> actBranchIds, Set<SparePermission> permissions) {
        public boolean isOwner() {
            return role == Role.OWNER;
        }

        public boolean mayOpenDashboard() {
            return role == Role.OWNER || role == Role.INVENTORY_MANAGER;
        }
    }

    private final OrganizationRepository orgRepo;
    private final AppUserRepository userRepo;
    private final UserBranchAccessRepository branchAccessRepo;
    private final BranchRepository branchRepo;
    private final SparePartsPermissionService permissionService;

    public SparePartsAccess(OrganizationRepository orgRepo,
                            AppUserRepository userRepo,
                            UserBranchAccessRepository branchAccessRepo,
                            BranchRepository branchRepo,
                            SparePartsPermissionService permissionService) {
        this.orgRepo = orgRepo;
        this.userRepo = userRepo;
        this.branchAccessRepo = branchAccessRepo;
        this.branchRepo = branchRepo;
        this.permissionService = permissionService;
    }

    /** For every Spare Parts endpoint: module on, and the caller may look at stock. */
    @Transactional(readOnly = true)
    public Caller requireViewer() {
        Long orgId = TenantContext.requireOrgId();
        requireModuleOn(orgId);
        Caller caller = resolve(orgId);
        if (!VIEWERS.contains(caller.role())) {
            throw DamsException.forbidden("Your role cannot use Spare Parts");
        }
        return caller;
    }

    /** For the Owner-only endpoints (permission grid): module on and the caller's own role is Owner. */
    @Transactional(readOnly = true)
    public Caller requireOwner() {
        Caller caller = requireViewer();
        if (!caller.isOwner()) {
            throw DamsException.forbidden("Only the Owner can change this");
        }
        return caller;
    }

    /** The grid allows {@code permission} for the caller's role AND the branch is one the caller works at. */
    public void require(Caller caller, SparePermission permission, Long branchId) {
        if (caller.isOwner()) {
            throw DamsException.forbidden(
                "The Owner has view-only access to Spare Parts. Use Switch role to act as Inventory Manager at a branch.");
        }
        if (!caller.permissions().contains(permission)) {
            throw DamsException.forbidden("Your role may not " + permission.label().toLowerCase()
                + ". The Owner controls this in Masters > Spare Parts.");
        }
        requireActsAt(caller, branchId);
    }

    /** The role's grid allows the permission, without naming a branch (used where each row names its own). */
    public void requirePermission(Caller caller, SparePermission permission) {
        if (caller.isOwner()) {
            throw DamsException.forbidden(
                "The Owner has view-only access to Spare Parts. Use Switch role to act as Inventory Manager at a branch.");
        }
        if (!caller.permissions().contains(permission)) {
            throw DamsException.forbidden("Your role may not " + permission.label().toLowerCase()
                + ". The Owner controls this in Masters > Spare Parts.");
        }
    }

    public void requireActsAt(Caller caller, Long branchId) {
        if (!caller.actBranchIds().contains(branchId)) {
            throw DamsException.forbidden("You can only do this at your own branch. Branch id " + branchId
                + " is not one of yours.");
        }
    }

    /** Whether the caller has the permission at the branch, without throwing (to decide which buttons show). */
    public boolean may(Caller caller, SparePermission permission, Long branchId) {
        return !caller.isOwner() && caller.permissions().contains(permission)
            && caller.actBranchIds().contains(branchId);
    }

    /**
     * What the shell asks once after login to decide whether to show the Spare Parts menu. Never throws for
     * "module off" or "no org" (Super Admin): those are simply {@code enabled=false}.
     */
    @Transactional(readOnly = true)
    public AccessInfo describe() {
        Long orgId = TenantContext.getOrgId();
        if (orgId == null || !isModuleOn(orgId)) {
            return new AccessInfo(false, false, false, false, null, List.of(), List.of(), List.of());
        }
        Caller caller = resolve(orgId);
        boolean canView = VIEWERS.contains(caller.role());
        List<BranchInfo> branches = canView ? activeBranches(orgId) : List.of();
        return new AccessInfo(true, canView, canView && caller.mayOpenDashboard(), caller.isOwner(),
            caller.role().name(),
            caller.actBranchIds().stream().sorted().toList(),
            caller.permissions().stream().map(Enum::name).sorted().toList(),
            branches);
    }

    public List<BranchInfo> activeBranches(Long orgId) {
        return branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .filter(Branch::isActive)
            .map(b -> new BranchInfo(b.getId(), b.getCode(), b.getName()))
            .toList();
    }

    // --- internals ---

    private void requireModuleOn(Long orgId) {
        if (!isModuleOn(orgId)) {
            throw DamsException.forbidden("Spare Parts is not turned on for this organization (id " + orgId + ")");
        }
    }

    private boolean isModuleOn(Long orgId) {
        return orgRepo.findById(orgId).map(Organization::isSparePartsEnabled).orElse(false);
    }

    private Caller resolve(Long orgId) {
        Long userId = currentUserId();
        AppUser user = userRepo.findByIdAndOrganization_Id(userId, orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));
        Role role = ActingDetails.effectiveRole(user);

        Set<Long> branches = Set.of();
        Set<SparePermission> permissions = EnumSet.noneOf(SparePermission.class);
        if (role.isSparePartsRole()) {
            // Switched into the role: only the one branch picked. Otherwise: every branch assigned to the user.
            branches = ActingDetails.isActing()
                ? (ActingDetails.actingBranch() == null ? Set.of() : Set.of(ActingDetails.actingBranch()))
                : branchAccessRepo.findByUserId(user.getId()).stream()
                    .map(UserBranchAccess::getBranchId)
                    .collect(Collectors.toSet());
            permissions = permissionService.effective(orgId, role);
        }
        return new Caller(userId, orgId, role, branches, permissions);
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw DamsException.forbidden("No authenticated user in context");
        }
        return userId;
    }
}
