package com.dams.config;

import com.dams.auth.util.JwtUtil;
import com.dams.user.entity.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The cross-org enforcement stub for Stage 1: proves that the tenant is set ONCE per
 * request from the JWT principal, and that SUPER_ADMIN is deliberately left un-scoped.
 * Row-level isolation assertions arrive in Stage 3 with the first tenant-scoped entity.
 */
class TenantFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final TenantFilter filter = new TenantFilter(jwtUtil);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void setsOrgId_forNonSuperAdminRole() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtUtil.getRole(claims)).thenReturn(Role.CASHIER);
        when(jwtUtil.getOrgId(claims)).thenReturn(77L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtConfig.ATTR_JWT_CLAIMS, claims);

        AtomicReference<Long> orgIdDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> orgIdDuringChain.set(TenantContext.getOrgId());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(orgIdDuringChain.get()).isEqualTo(77L);
        // Cleared once the request completes so the pooled thread carries nothing forward
        assertThat(TenantContext.getOrgId()).isNull();
    }

    @Test
    void leavesTenantUnset_forSuperAdmin() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtUtil.getRole(claims)).thenReturn(Role.SUPER_ADMIN);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtConfig.ATTR_JWT_CLAIMS, claims);

        AtomicReference<Long> orgIdDuringChain = new AtomicReference<>(-1L);
        FilterChain chain = (req, res) -> orgIdDuringChain.set(TenantContext.getOrgId());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(orgIdDuringChain.get()).isNull();
    }

    @Test
    void leavesTenantUnset_whenNoJwtClaimsPresent() throws Exception {
        AtomicReference<Long> orgIdDuringChain = new AtomicReference<>(-1L);
        FilterChain chain = (req, res) -> orgIdDuringChain.set(TenantContext.getOrgId());

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(orgIdDuringChain.get()).isNull();
    }
}
