package com.dams.auth.service;

import com.dams.auth.dto.AcceptInviteRequest;
import com.dams.auth.dto.ChangePasswordRequest;
import com.dams.auth.dto.LoginRequest;
import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.auth.dto.LoginResponse;
import com.dams.auth.dto.SwitchOptionsResponse;
import com.dams.auth.dto.SwitchRoleRequest;
import com.dams.auth.util.JwtUtil;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.ActingDetails;
import com.dams.config.TenantContext;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.entity.UserRoleGrant;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import com.dams.user.repository.UserRoleGrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles login and invite acceptance. There is no token refresh in v1 — a single
 * short-lived access token is issued and the client re-logs in on expiry (plan.md rev 3).
 *
 * State transitions are logged at INFO with the user and org so a session can be traced.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** The roles a user can ever switch into — never Owner or Super Admin. */
    private static final Set<Role> SWITCHABLE_ROLES =
        EnumSet.of(Role.FINANCE_MANAGER, Role.ACCOUNTANT, Role.CASHIER);

    private final AppUserRepository userRepo;
    private final UserBranchAccessRepository branchAccessRepo;
    private final UserRoleGrantRepository grantRepo;
    private final BranchRepository branchRepo;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(AppUserRepository userRepo,
                       UserBranchAccessRepository branchAccessRepo,
                       UserRoleGrantRepository grantRepo,
                       BranchRepository branchRepo,
                       AuditService auditService,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.branchAccessRepo = branchAccessRepo;
        this.grantRepo = grantRepo;
        this.branchRepo = branchRepo;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Authenticate by email + password and return an access token plus the identity
     * the frontend shell needs (role, org, home branch, name).
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Emails are stored lowercased (see UserService/AdminOrgService) — normalise here so
        // mixed-case login attempts resolve to the same account.
        String email = request.getEmail().trim().toLowerCase();
        AppUser user = userRepo.findByEmailIgnoreCase(email)
            .orElseThrow(() -> DamsException.notFound("AppUser", "email", email));

        if (!user.isActive()) {
            throw DamsException.forbidden("AppUser " + user.getId() + " is deactivated");
        }

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Do not reveal which of the two was wrong
            throw DamsException.badRequest("Invalid email or password");
        }

        log.info("Login success: userId={} role={} orgId={}",
            user.getId(), user.getRole(), orgIdOf(user));

        return buildLoginResponse(user);
    }

    /**
     * Accept an invite: validate the token, set the password, log the user in.
     */
    @Transactional
    public LoginResponse acceptInvite(AcceptInviteRequest request) {
        AppUser user = userRepo.findByInviteToken(request.getToken())
            .orElseThrow(() -> DamsException.badRequest("Invite token not found or already used"));

        if (user.getInviteExpiresAt() == null
                || user.getInviteExpiresAt().isBefore(Instant.now())) {
            throw DamsException.badRequest(
                "Invite token for AppUser " + user.getId() + " has expired");
        }

        // Set the password and clear the invite token — it can only be used once
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setInviteToken(null);
        user.setInviteExpiresAt(null);
        userRepo.save(user);

        log.info("Invite accepted: userId={} role={} orgId={}",
            user.getId(), user.getRole(), orgIdOf(user));

        return buildLoginResponse(user);
    }

    /**
     * Change the signed-in user's own password. Works for every role, including Super Admin
     * (its seeded password is meant to be changed here on first login).
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        AppUser user = userRepo.findById(userId)
            .orElseThrow(() -> DamsException.notFound("AppUser", userId));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw DamsException.badRequest("Current password is incorrect");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw DamsException.badRequest("New password must differ from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepo.save(user);

        log.info("Password changed: userId={} role={}", user.getId(), user.getRole());
    }

    // --- Role switching (plan.md rev 55) ---

    /**
     * The branches the caller may switch into and the roles available at each. An Owner may act
     * as any role at any active branch; everyone else only gets what the Owner granted them.
     * Their own primary role is never listed — "switch back" is a separate action.
     */
    @Transactional(readOnly = true)
    public SwitchOptionsResponse switchOptions(Long userId) {
        Long orgId = TenantContext.requireOrgId();
        AppUser user = requireOrgUser(userId, orgId);

        List<Branch> branches = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .filter(Branch::isActive)
            .toList();

        Map<Long, Set<Role>> rolesByBranch = new LinkedHashMap<>();
        branches.forEach(b -> rolesByBranch.put(b.getId(), EnumSet.noneOf(Role.class)));

        if (user.getRole() == Role.OWNER) {
            rolesByBranch.values().forEach(r -> r.addAll(SWITCHABLE_ROLES));
        } else {
            for (UserRoleGrant grant : grantRepo.findByUserId(userId)) {
                if (grant.getRole() == Role.FINANCE_MANAGER) {
                    rolesByBranch.values().forEach(r -> r.add(Role.FINANCE_MANAGER)); // org-wide
                } else if (rolesByBranch.containsKey(grant.getBranchId())) {
                    rolesByBranch.get(grant.getBranchId()).add(grant.getRole());
                }
            }
        }

        List<SwitchOptionsResponse.BranchOption> options = branches.stream()
            .map(b -> new SwitchOptionsResponse.BranchOption(b.getId(), b.getCode(), b.getName(),
                rolesByBranch.get(b.getId()).stream()
                    .filter(r -> r != user.getRole())
                    .sorted()
                    .toList()))
            .filter(o -> !o.roles().isEmpty())
            .toList();

        Role acting = ActingDetails.actingRole();
        return new SwitchOptionsResponse(user.getRole(), acting != null ? acting : user.getRole(),
            ActingDetails.actingBranch(), options);
    }

    /**
     * Issues a new token acting as {@code request.role} at {@code request.branchId}. Naming the
     * user's own role switches back. The client never chooses its own role: the grant is checked
     * here (and again on every request by {@code ActingRoleFilter}).
     */
    @Transactional
    public LoginResponse switchRole(Long userId, SwitchRoleRequest request) {
        Long orgId = TenantContext.requireOrgId();
        AppUser user = requireOrgUser(userId, orgId);
        Role from = ActingDetails.actingRole() != null ? ActingDetails.actingRole() : user.getRole();
        Role target = request.getRole();

        if (target == user.getRole()) {
            auditSwitch(user, from, target, ActingDetails.actingBranch());
            log.info("Role switch back: userId={} to={}", userId, target);
            return buildResponse(user, target, null);
        }
        if (target == Role.OWNER || target == Role.SUPER_ADMIN) {
            throw DamsException.badRequest("You cannot switch into the " + target + " role");
        }
        Long branchId = request.getBranchId();
        if (branchId == null) {
            throw DamsException.badRequest("Pick the branch you want to work at");
        }
        Branch branch = branchRepo.findByIdAndOrgId(branchId, orgId)
            .orElseThrow(() -> DamsException.notFound("Branch", branchId));
        if (!branch.isActive()) {
            throw DamsException.badRequest("Branch '" + branch.getCode() + "' is inactive");
        }
        if (!mayAct(user, target, branchId)) {
            throw DamsException.forbidden("You have not been given the " + target
                + " role at branch '" + branch.getCode() + "'");
        }

        auditSwitch(user, from, target, branchId);
        log.info("Role switch: userId={} from={} to={} branchId={}", userId, from, target, branchId);
        return buildResponse(user, target, branchId);
    }

    private boolean mayAct(AppUser user, Role target, Long branchId) {
        if (!SWITCHABLE_ROLES.contains(target)) {
            return false;
        }
        if (user.getRole() == Role.OWNER) {
            return true; // the Owner may act as any role at any branch of their org
        }
        return target == Role.FINANCE_MANAGER
            ? grantRepo.existsByUserIdAndRoleAndBranchIdIsNull(user.getId(), target)
            : grantRepo.existsByUserIdAndRoleAndBranchId(user.getId(), target, branchId);
    }

    private void auditSwitch(AppUser user, Role from, Role to, Long branchId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", from.name());
        detail.put("to", to.name());
        detail.put("branchId", branchId);
        auditService.recordUserEvent("AppUser", user.getId(), branchId, EventType.ROLE_SWITCHED,
            user.getId(), detail);
    }

    private AppUser requireOrgUser(Long userId, Long orgId) {
        return userRepo.findByIdAndOrganization_Id(userId, orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));
    }

    // --- Private helpers ---

    private LoginResponse buildLoginResponse(AppUser user) {
        return buildResponse(user, user.getRole(), null);
    }

    /**
     * @param actingRole     the role the token authorises (the user's own role unless switched)
     * @param actingBranchId the one branch a switched session is scoped to (null when in own role)
     */
    private LoginResponse buildResponse(AppUser user, Role actingRole, Long actingBranchId) {
        boolean switched = actingRole != user.getRole();
        Long orgId = orgIdOf(user);

        // While switched, the token's branch claims describe the ACTING role at the picked branch,
        // so the frontend's existing branchIds / homeBranchId reads stay correct.
        List<Long> branchIds = switched
            ? (actingRole == Role.ACCOUNTANT ? List.of(actingBranchId) : List.of())
            : loadBranchIds(user);
        Long homeBranchId = switched
            ? (actingRole == Role.CASHIER ? actingBranchId : null)
            : user.getHomeBranchId();

        String accessToken = jwtUtil.generateAccessToken(
            user.getId(), orgId, actingRole, user.getRole(), actingBranchId, branchIds, homeBranchId);

        boolean canSwitch = user.getRole() == Role.OWNER
            || !grantRepo.findByUserId(user.getId()).isEmpty();

        return new LoginResponse(accessToken, actingRole, orgId, homeBranchId, user.getName(),
            user.getRole(), actingBranchId, canSwitch);
    }

    private List<Long> loadBranchIds(AppUser user) {
        return branchAccessRepo.findByUserId(user.getId()).stream()
            .map(access -> access.getBranchId())
            .toList();
    }

    private Long orgIdOf(AppUser user) {
        return user.getOrganization() != null ? user.getOrganization().getId() : null;
    }
}
