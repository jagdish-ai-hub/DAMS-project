package com.dams.config;

import com.dams.branch.repository.BranchRepository;
import com.dams.common.dto.ApiError;
import com.dams.common.filter.RequestIdFilter;
import com.dams.common.security.ActingDetails;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserRoleGrantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Re-checks a SWITCHED role against the database on every request (plan.md rev 55).
 *
 * The JWT's {@code role} is the acting role, so a grant the Owner later removes would otherwise
 * stay usable until the ~8h token expires. Same principle as {@code BranchScope}: read access
 * fresh from the DB. When the acting role is no longer valid the request gets a 401, and the
 * frontend's existing 401 handler returns the user to the login screen.
 *
 * Runs after {@link TenantFilter}, so {@code TenantContext} is already set. Requests in the
 * user's own role are not touched.
 */
@Component
public class ActingRoleFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ActingRoleFilter.class);

    private final AppUserRepository userRepo;
    private final UserRoleGrantRepository grantRepo;
    private final BranchRepository branchRepo;
    private final ObjectMapper objectMapper;

    public ActingRoleFilter(AppUserRepository userRepo,
                            UserRoleGrantRepository grantRepo,
                            BranchRepository branchRepo,
                            ObjectMapper objectMapper) {
        this.userRepo = userRepo;
        this.grantRepo = grantRepo;
        this.branchRepo = branchRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId
                && auth.getDetails() instanceof ActingDetails details
                && !auth.getAuthorities().isEmpty()) {

            Role acting = Role.valueOf(auth.getAuthorities().iterator().next().getAuthority());
            if (details.isSwitched(acting) && !stillAllowed(userId, acting, details)) {
                log.warn("Acting role no longer valid: userId={} acting={} primary={} branch={}",
                    userId, acting, details.primaryRole(), details.actingBranchId());
                SecurityContextHolder.clearContext();
                writeUnauthorized(response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean stillAllowed(Long userId, Role acting, ActingDetails details) {
        Long orgId = TenantContext.getOrgId();
        if (orgId == null || acting == Role.OWNER || acting == Role.SUPER_ADMIN) {
            return false; // nobody switches INTO Owner / Super Admin
        }
        AppUser user = userRepo.findByIdAndOrganization_Id(userId, orgId).orElse(null);
        if (user == null || !user.isActive() || user.getRole() != details.primaryRole()) {
            return false; // deactivated, or their own role changed since the token was issued
        }
        Long branchId = details.actingBranchId();
        if (branchId == null) {
            return false; // a switched session is always scoped to a branch
        }
        if (user.getRole() == Role.OWNER) {
            // The Owner may act as any role, at any branch of their own org.
            return branchRepo.findByIdAndOrgId(branchId, orgId).isPresent();
        }
        return acting == Role.FINANCE_MANAGER
            ? grantRepo.existsByUserIdAndRoleAndBranchIdIsNull(userId, acting)
            : grantRepo.existsByUserIdAndRoleAndBranchId(userId, acting, branchId);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        ApiError error = new ApiError(MDC.get(RequestIdFilter.MDC_KEY), HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(), "Your acting role was removed — sign in again");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }
}
