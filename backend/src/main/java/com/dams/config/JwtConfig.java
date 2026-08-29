package com.dams.config;

import com.dams.auth.util.JwtUtil;
import com.dams.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the JWT from the Authorization header, validates it, and sets the
 * Spring Security context + MDC orgId for the request.
 *
 * Sets orgId in MDC so every downstream log line carries it automatically —
 * see the logging pattern in application.yml.
 */
@Component
public class JwtConfig extends OncePerRequestFilter {

    static final String ATTR_JWT_CLAIMS = "jwtClaims";

    private final JwtUtil jwtUtil;

    public JwtConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parseToken(token);

                Long userId = jwtUtil.getUserId(claims);
                Long orgId  = jwtUtil.getOrgId(claims);
                Role role   = jwtUtil.getRole(claims);

                if (orgId != null) {
                    MDC.put("orgId", String.valueOf(orgId));
                }

                // Stash the parsed claims for TenantFilter (which runs next) to consume
                request.setAttribute(ATTR_JWT_CLAIMS, claims);

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority(role.name()))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (JwtException | IllegalArgumentException ignored) {
                // Invalid/expired token — leave the context unauthenticated.
                // Spring Security returns 401 for protected routes.
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("orgId");
        }
    }
}
