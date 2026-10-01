package com.dams.user.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.email.EmailService;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.dto.RoleGrantDto;
import com.dams.user.dto.UserRequest;
import com.dams.user.dto.UserResponse;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserBranchAccess;
import com.dams.user.entity.UserRoleGrant;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import com.dams.user.repository.UserRoleGrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owner-managed team CRUD, org-scoped. Creating a user issues an invite (no password is
 * set here — the user sets their own via the accept-invite link, same as the Owner flow).
 * Branch binding follows the role: OWNER/FM are org-wide, ACCOUNTANT gets access rows,
 * CASHIER gets exactly one home branch. Extra switchable roles (rev 55) are stored as
 * user_role_grant rows and replaced wholesale on every save. See AGENT.md / plan.md.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int INVITE_EXPIRY_DAYS = 7;

    private final AppUserRepository userRepo;
    private final UserBranchAccessRepository branchAccessRepo;
    private final UserRoleGrantRepository grantRepo;
    private final BranchRepository branchRepo;
    private final OrganizationRepository orgRepo;
    private final EmailService emailService;
    private final String appBaseUrl;

    public UserService(AppUserRepository userRepo,
                       UserBranchAccessRepository branchAccessRepo,
                       UserRoleGrantRepository grantRepo,
                       BranchRepository branchRepo,
                       OrganizationRepository orgRepo,
                       EmailService emailService,
                       @Value("${dams.app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.userRepo = userRepo;
        this.branchAccessRepo = branchAccessRepo;
        this.grantRepo = grantRepo;
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

        List<AppUser> users = userRepo.findByOrganization_IdOrderByNameAsc(orgId);
        Map<Long, List<UserRoleGrant>> grantsByUser = users.isEmpty() ? Map.of()
            : grantRepo.findByUserIdIn(users.stream().map(AppUser::getId).toList()).stream()
                .collect(Collectors.groupingBy(UserRoleGrant::getUserId));

        return users.stream()
            .map(u -> toResponse(u, branchIdsOf(u), branchesById, grantsByUser.getOrDefault(u.getId(), List.of()), null))
            .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        AppUser user = load(id);
        Long orgId = TenantContext.requireOrgId();
        Map<Long, Branch> branchesById = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, b -> b));
        return toResponse(user, branchIdsOf(user), branchesById, grantRepo.findByUserId(user.getId()), null);
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
        List<UserRoleGrant> grants = resolveRoleGrants(orgId, role, request);

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
        grants = saveRoleGrants(user.getId(), grants);

        String inviteLink = buildInviteLink(token);
        emailService.sendUserInvite(email, org.getName(), roleLabel(role), inviteLink);

        log.info("User created: orgId={} userId={} role={} email='{}'", orgId, user.getId(), role, email);

        Map<Long, Branch> branchesById = loadOrgBranches(orgId);
        return toResponse(user, branchIds, branchesById, grants, inviteLink);
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
        List<UserRoleGrant> grants = resolveRoleGrants(orgId, newRole, request);

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
        // The submitted list replaces the user's grants wholesale (rev 55)
        grantRepo.deleteByUserId(user.getId());
        grantRepo.flush();
        grants = saveRoleGrants(user.getId(), grants);

        log.info("User updated: orgId={} userId={} role={} active={}", orgId, user.getId(), newRole, user.isActive());

        Map<Long, Branch> branchesById = loadOrgBranches(orgId);
        return toResponse(user, branchIds, branchesById, grants, null);
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

    /**
     * Validates the extra switchable roles (rev 55) and returns the rows to store. An OWNER gets
     * none (the Owner can already act as any role); a grant equal to the user's own role is
     * dropped; FINANCE_MANAGER is org-wide (one row, no branch); ACCOUNTANT / CASHIER need at
     * least one branch of this org.
     */
    private List<UserRoleGrant> resolveRoleGrants(Long orgId, Role primary, UserRequest request) {
        List<RoleGrantDto> requested = request.getRoleGrants();
        if (requested == null || requested.isEmpty() || primary == Role.OWNER) {
            return List.of();
        }
        Map<Role, Set<Long>> branchesByRole = new EnumMap<>(Role.class);
        for (RoleGrantDto dto : requested) {
            Role role = dto.role();
            if (role == null || role == primary) {
                continue;
            }
            if (role == Role.OWNER || role == Role.SUPER_ADMIN) {
                throw DamsException.badRequest("A user cannot be given the " + roleLabel(role) + " role to switch into");
            }
            Set<Long> ids = branchesByRole.computeIfAbsent(role, r -> new java.util.LinkedHashSet<>());
            if (role != Role.FINANCE_MANAGER) {
                if (dto.branchIds() != null) {
                    ids.addAll(dto.branchIds());
                }
                if (ids.isEmpty()) {
                    throw DamsException.badRequest("Pick at least one branch for the extra " + roleLabel(role) + " role");
                }
                ids.forEach(bid -> requireBranchInOrg(orgId, bid));
            }
        }
        List<UserRoleGrant> grants = new ArrayList<>();
        branchesByRole.forEach((role, ids) -> {
            if (role == Role.FINANCE_MANAGER) {
                grants.add(newGrant(orgId, role, null));
            } else {
                ids.forEach(bid -> grants.add(newGrant(orgId, role, bid)));
            }
        });
        return grants;
    }

    private UserRoleGrant newGrant(Long orgId, Role role, Long branchId) {
        UserRoleGrant g = new UserRoleGrant();
        g.setOrgId(orgId);
        g.setRole(role);
        g.setBranchId(branchId);
        return g;
    }

    private List<UserRoleGrant> saveRoleGrants(Long userId, List<UserRoleGrant> grants) {
        grants.forEach(g -> g.setUserId(userId));
        return grants.isEmpty() ? grants : grantRepo.saveAll(grants);
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
                                    Map<Long, Branch> branchesById, List<UserRoleGrant> grants,
                                    String inviteLink) {
        List<RoleGrantDto> grantDtos = toGrantDtos(grants);
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
            grantDtos.isEmpty() ? null : grantDtos,
            grantDtos.isEmpty() ? null : extraRolesLabel(grantDtos, branchesById),
            user.getCreatedAt(),
            inviteLink);
    }

    /** One dto per granted role, its branches in stable order (FINANCE_MANAGER has none). */
    private List<RoleGrantDto> toGrantDtos(List<UserRoleGrant> grants) {
        Map<Role, List<Long>> byRole = new EnumMap<>(Role.class);
        for (UserRoleGrant g : grants) {
            List<Long> ids = byRole.computeIfAbsent(g.getRole(), r -> new ArrayList<>());
            if (g.getBranchId() != null) {
                ids.add(g.getBranchId());
            }
        }
        List<RoleGrantDto> out = new ArrayList<>();
        byRole.forEach((role, ids) -> out.add(new RoleGrantDto(role, ids.stream().sorted().toList())));
        return out;
    }

    /** e.g. "Cashier (OOR), Finance Manager" - branch codes, not names, to stay short in the table. */
    private String extraRolesLabel(List<RoleGrantDto> grants, Map<Long, Branch> branchesById) {
        return grants.stream()
            .map(g -> g.branchIds().isEmpty()
                ? roleLabel(g.role())
                : roleLabel(g.role()) + " (" + g.branchIds().stream()
                    .map(id -> branchesById.containsKey(id) ? branchesById.get(id).getCode() : "#" + id)
                    .collect(Collectors.joining(", ")) + ")")
            .collect(Collectors.joining(", "));
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
