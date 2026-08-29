package com.dams.admin;

import com.dams.admin.controller.AdminOrgController;
import com.dams.admin.service.AdminOrgService;
import com.dams.auth.util.JwtUtil;
import com.dams.config.JwtConfig;
import com.dams.config.SecurityConfig;
import com.dams.config.TenantFilter;
import com.dams.user.entity.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Super Admin org endpoints are the ONLY cross-org queries in the system.
 * These tests prove they are reachable by SUPER_ADMIN alone.
 */
@WebMvcTest(AdminOrgController.class)
@Import({SecurityConfig.class, JwtConfig.class, TenantFilter.class})
class AdminOrgControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminOrgService adminOrgService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void listOrganizations_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/organizations"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listOrganizations_returns403_whenCallerIsNotSuperAdmin() throws Exception {
        stubToken("owner-token", 5L, 1L, Role.OWNER);

        mockMvc.perform(get("/api/v1/admin/organizations").header("Authorization", "Bearer owner-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void listOrganizations_returns200_whenCallerIsSuperAdmin() throws Exception {
        stubToken("admin-token", 1L, null, Role.SUPER_ADMIN);
        when(adminOrgService.listOrganizations()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/organizations").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isOk());
    }

    private void stubToken(String token, Long userId, Long orgId, Role role) {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken(token)).thenReturn(claims);
        when(jwtUtil.getUserId(claims)).thenReturn(userId);
        when(jwtUtil.getOrgId(claims)).thenReturn(orgId);
        when(jwtUtil.getRole(any(Claims.class))).thenReturn(role);
    }
}
