package com.dams.config;

import com.dams.auth.util.JwtUtil;
import com.dams.user.entity.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Multi-tenancy enforcement — sets the current request's org_id ONCE, from the
 * authenticated principal, so no individual query has to remember it. See AGENT.md.
 *
 * Reads the parsed JWT claims stashed by JwtConfig, then:
 *   - SUPER_ADMIN: leaves TenantContext empty — its queries are intentionally cross-org
 *     and live in their own controller/service package.
 *   - Every other role: stores org_id in a request-scoped ThreadLocal (TenantContext).
 *
 * TenantFilterActivator reads TenantContext and enables the Hibernate "orgFilter" on the
 * session for every repository call. Tenant-scoped entities opt into that filter starting
 * in Stage 3 (Customer, Vehicle, …); until then this wiring is inert but proven.
 *
 * IMPORTANT: a forgotten tenant filter is the most common multi-tenant security bug.
 * This class is the single place that sets the tenant — do not bypass it.
 */
// Placed immediately after JwtConfig in the security chain by SecurityConfig (addFilterAfter).
@Component
public class TenantFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public TenantFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Claims claims = (Claims) request.getAttribute(JwtConfig.ATTR_JWT_CLAIMS);

        if (claims != null) {
            Role role = jwtUtil.getRole(claims);
            Long orgId = jwtUtil.getOrgId(claims);

            if (role != Role.SUPER_ADMIN && orgId != null) {
                TenantContext.setOrgId(orgId);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear — thread-pool threads are reused across requests
            TenantContext.clear();
        }
    }
}
