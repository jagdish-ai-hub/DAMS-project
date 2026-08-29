package com.dams.auth.service;

import com.dams.auth.dto.AcceptInviteRequest;
import com.dams.auth.dto.ChangePasswordRequest;
import com.dams.auth.dto.LoginRequest;
import com.dams.auth.dto.LoginResponse;
import com.dams.auth.util.JwtUtil;
import com.dams.common.exception.DamsException;
import com.dams.user.entity.AppUser;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserBranchAccessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Handles login and invite acceptance. There is no token refresh in v1 — a single
 * short-lived access token is issued and the client re-logs in on expiry (plan.md rev 3).
 *
 * State transitions are logged at INFO with the user and org so a session can be traced.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository userRepo;
    private final UserBranchAccessRepository branchAccessRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(AppUserRepository userRepo,
                       UserBranchAccessRepository branchAccessRepo,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.branchAccessRepo = branchAccessRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Authenticate by email + password and return an access token plus the identity
     * the frontend shell needs (role, org, home branch, name).
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepo.findByEmail(request.getEmail())
            .orElseThrow(() -> DamsException.notFound("AppUser", "email", request.getEmail()));

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

    // --- Private helpers ---

    private LoginResponse buildLoginResponse(AppUser user) {
        List<Long> branchIds = loadBranchIds(user);
        Long orgId = orgIdOf(user);

        String accessToken = jwtUtil.generateAccessToken(
            user.getId(), orgId, user.getRole(), branchIds, user.getHomeBranchId());

        return new LoginResponse(accessToken, user.getRole(), orgId, user.getHomeBranchId(), user.getName());
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
