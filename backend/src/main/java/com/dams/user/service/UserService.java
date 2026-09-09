package com.dams.user.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.email.EmailService;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.dto.UserRequest;
import com.dams.user.dto.UserResponse;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserBranchAccess;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owner-managed team CRUD, org-scoped. Creating a user issues an invite (no password is
 * set here — the user sets their own via the accept-invite link, same as the Owner flow).
 * Branch binding follows the role: OWNER/FM are org-wide, ACCOUNTANT gets access rows,
 * CASHIER gets exactly one home branch. See AGENT.md / plan.md.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int INVITE_EXPIRY_DAYS = 7;

    private final AppUserRepository userRepo;
    private final UserBranchAccessRepository branchAccessRepo;
    private final BranchRepository branchRepo;
    private final OrganizationRepository orgRepo;
    private final EmailService emailService;
    private final String appBaseUrl;

    public UserService(AppUserRepository userRepo,
                       UserBranchAccessRepository branchAccessRepo,
                       BranchRepository branchRepo,
                       OrganizationRepository orgRepo,
                       EmailService emailService,
                       @Value("${dams.app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.userRepo = userRepo;
        this.branchAccessRepo = branchAccessRepo;
        this.branchRepo = branchRepo;
        this.orgRepo = orgRepo;
        this.emailService = emailService;
        this.appBaseUrl = appBaseUrl;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        Long orgId = TenantContext.requireOrgId();
        Map<Long, Branch> branchesById = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, b -> b, (a, b) -> a, LinkedHashMap::new));

        return userRepo.findByOrganization_IdOrderByNameAsc(orgId).stream()
            .map(u -> toResponse(u, branchIdsOf(u), branchesById, null))
            .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        AppUser user = load(id);
        Long orgId = TenantContext.requireOrgId();
        Map<Long, Branch> branchesById = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, b -> b));
        return toResponse(user, branchIdsOf(user), branchesById, null);
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        Long orgId = TenantContext.requireOrgId();
        Role role = request.getRole();
        rejectSuperAdmin(role);

        String email = request.getEmail().trim().toLowerCase();
        if (userRepo.existsByEmailIgnoreCase(email)) {
            throw DamsException.conflict("A user with email '" + email + "' already exists");
        }

        List<Long> branchIds = resolveBranchIds(orgId, role, request);
        Long homeBranchId = role == Role.CASHIER ? branchIds.get(0) : null;

        Organization org = orgRepo.getReferenceById(orgId);
        String token = UUID.randomUUID().toString();

        AppUser user = new AppUser();
        user.setOrganization(org);
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        user.setHomeBranchId(homeBranchId);
        user.setInviteToken(token);
        user.setInviteExpiresAt(Instant.now().plus(INVITE_EXPIRY_DAYS, ChronoUnit.DAYS));
        user = userRepo.save(user);

        if (role == Role.ACCOUNTANT) {
            replaceBranchAccess(user.getId(), branchIds);
        }

        String inviteLink = buildInviteLink(token);
        emailService.sendUserInvite(email, org.getName(), roleLabel(role), inviteLink);

        log.info("User created: orgId={} userId={} role={} email='{}'", orgId, user.getId(), role, email);

        Map<Long, Branch> branchesById = loadOrgBranches(orgId);
        return toResponse(user, branchIds, branchesById, inviteLink);
    }

    @Transactional
    public UserResponse update(Long id, UserRequest request, Long callerUserId) {
        Long orgId = TenantContext.requireOrgId();
        AppUser user = load(id);
        Role newRole = request.getRole();
        rejectSuperAdmin(newRole);

        boolean deactivating = Boolean.FALSE.equals(request.getActive());
        boolean losingOwner = user.getRole() == Role.OWNER && (newRole != Role.OWNER || deactivating);

        if (user.getId().equals(callerUserId) && (deactivating || (user.getRole() == Role.OWNER && newRole != Role.OWNER))) {
            throw DamsException.badRequest("You cannot remove or deactivate your own Owner access");
        }
        if (losingOwner && countActiveOwners(orgId) <= 1) {
            throw DamsException.badRequest("The organization must keep at least one active Owner");
        }

        List<Long> branchIds = resolveBranchIds(orgId, newRole, request);

        user.setName(request.getName().trim());
        user.setRole(newRole);
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }
        user.setHomeBranchId(newRole == Role.CASHIER ? branchIds.get(0) : null);
        userRepo.save(user);

        // Rebuild branch-access rows to match the (possibly new) role
        branchAccessRepo.deleteByUserId(user.getId());
        if (newRole == Role.ACCOUNTANT) {
            replaceBranchAccess(user.getId(), branchIds);
        }

        log.info("User updated: orgId={} userId={} role={} active={}", orgId, user.getId(), newRole, user.isActive());

        Map<Long, Branch> branchesById = loadOrgBranches(orgId);
        return toResponse(user, branchIds, branchesById, null);
    }

    // --- helpers ---

    private AppUser load(Long id) {
        return userRepo.findByIdAndOrganization_Id(id, TenantContext.requireOrgId())
            .orElseThrow(() -> DamsException.notFound("User", id));
    }

    private void rejectSuperAdmin(Role role) {
        if (role == Role.SUPER_ADMIN) {
            throw DamsException.badRequest("SUPER_ADMIN users are managed at the platform level, not by an Owner");
        }
    }

    /** Validates and returns the branch ids this role needs (empty for org-wide roles). */
    private List<Long> resolveBranchIds(Long orgId, Role role, UserRequest request) {
        if (role == Role.OWNER || role == Role.FINANCE_MANAGER) {
            return List.of();
        }
        if (role == Role.CASHIER) {
            Long branchId = request.getHomeBranchId();
            if (branchId == null) {
                throw DamsException.badRequest("A cashier needs exactly one home branch");
            }
            requireBranchInOrg(orgId, branchId);
            return List.of(branchId);
        }
        // ACCOUNTANT
        List<Long> ids = request.getBranchIds() == null ? List.of() : request.getBranchIds();
        if (ids.isEmpty()) {
            throw DamsException.badRequest("An accountant needs at least one assigned branch");
        }
        List<Long> distinct = ids.stream().distinct().toList();
        distinct.forEach(bid -> requireBranchInOrg(orgId, bid));
        return distinct;
    }

    private void requireBranchInOrg(Long orgId, Long branchId) {
        branchRepo.findByIdAndOrgId(branchId, orgId)
            .orElseThrow(() -> DamsException.notFound("Branch", branchId));
    }

    private void replaceBranchAccess(Long userId, List<Long> branchIds) {
        AppUser ref = userRepo.getReferenceById(userId);
        for (Long branchId : branchIds) {
            UserBranchAccess uba = new UserBranchAccess();
            UserBranchAccess.UserBranchAccessId key = new UserBranchAccess.UserBranchAccessId();
            key.setBranchId(branchId);
            uba.setId(key);
            uba.setUser(ref);
            branchAccessRepo.save(uba);
        }
    }

    private List<Long> branchIdsOf(AppUser user) {
        if (user.getRole() == Role.CASHIER) {
            return user.getHomeBranchId() == null ? List.of() : List.of(user.getHomeBranchId());
        }
        if (user.getRole() == Role.ACCOUNTANT) {
            return branchAccessRepo.findByUserId(user.getId()).stream()
                .map(UserBranchAccess::getBranchId)
                .toList();
        }
        return List.of();
    }

    private long countActiveOwners(Long orgId) {
        return userRepo.findByOrganization_Id(orgId).stream()
            .filter(u -> u.getRole() == Role.OWNER && u.isActive())
            .count();
    }

    private Map<Long, Branch> loadOrgBranches(Long orgId) {
        Map<Long, Branch> map = new LinkedHashMap<>();
        branchRepo.findByOrgIdOrderByCodeAsc(orgId).forEach(b -> map.put(b.getId(), b));
        return map;
    }

    private UserResponse toResponse(AppUser user, List<Long> branchIds,
                                    Map<Long, Branch> branchesById, String inviteLink) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getEmail(),
            user.getRole(),
            user.isActive(),
            user.getPasswordHash() == null,
            user.getHomeBranchId(),
            branchIds.isEmpty() ? null : new ArrayList<>(branchIds),
            branchAccessLabel(user.getRole(), branchIds, branchesById),
            user.getCreatedAt(),
            inviteLink);
    }

    private String branchAccessLabel(Role role, List<Long> branchIds, Map<Long, Branch> branchesById) {
        if (role == Role.OWNER || role == Role.FINANCE_MANAGER) {
            return "All branches";
        }
        if (branchIds.isEmpty()) {
            return "No branches";
        }
        return branchIds.stream()
            .map(id -> {
                Branch b = branchesById.get(id);
                return b != null ? b.getName() : ("#" + id);
            })
            .collect(Collectors.joining(", "));
    }

    private String roleLabel(Role role) {
        return switch (role) {
            case OWNER -> "Owner";
            case FINANCE_MANAGER -> "Finance Manager";
            case ACCOUNTANT -> "Accountant";
            case CASHIER -> "Cashier";
            case SUPER_ADMIN -> "Super Admin";
        };
    }

    private String buildInviteLink(String token) {
        return UriComponentsBuilder.fromUriString(appBaseUrl)
            .path("/accept-invite")
            .queryParam("token", token)
            .build()
            .toUriString();
    }
}
