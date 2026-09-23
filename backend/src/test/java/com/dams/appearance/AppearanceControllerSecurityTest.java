package com.dams.appearance;

import com.dams.appearance.controller.AppearanceController;
import com.dams.appearance.service.AppearanceService;
import com.dams.auth.util.JwtUtil;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The font read is public (login screen); the write is Super Admin only and allowlisted. */
@WebMvcTest(AppearanceController.class)
@Import({SecurityConfig.class, JwtConfig.class, TenantFilter.class})
class AppearanceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppearanceService appearanceService;

    @MockBean
    private BranchScope branchScope;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void getAppearance_isPublic() throws Exception {
        when(appearanceService.currentFont()).thenReturn("plex");

        mockMvc.perform(get("/api/v1/public/appearance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.font").value("plex"));
    }

    @Test
    void putAppearance_returns401_whenNoToken() throws Exception {
        mockMvc.perform(put("/api/v1/admin/appearance")
                .contentType(MediaType.APPLICATION_JSON).content("{\"font\":\"inter\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void putAppearance_returns403_whenCallerIsNotSuperAdmin() throws Exception {
        stubToken("owner-token", 5L, 1L, Role.OWNER);

        mockMvc.perform(put("/api/v1/admin/appearance").header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"font\":\"inter\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void putAppearance_returns200_whenCallerIsSuperAdmin() throws Exception {
        stubToken("admin-token", 1L, null, Role.SUPER_ADMIN);
        when(branchScope.currentUserId()).thenReturn(1L);
        when(appearanceService.setFont(eq("inter"), any())).thenReturn("inter");

        mockMvc.perform(put("/api/v1/admin/appearance").header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"font\":\"inter\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.font").value("inter"));
    }

    @Test
    void putAppearance_returns400_forUnknownFont() throws Exception {
        stubToken("admin-token", 1L, null, Role.SUPER_ADMIN);
        when(branchScope.currentUserId()).thenReturn(1L);
        when(appearanceService.setFont(eq("comic-sans"), any()))
            .thenThrow(DamsException.badRequest("Font 'comic-sans' is not one of the available fonts"));

        mockMvc.perform(put("/api/v1/admin/appearance").header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"font\":\"comic-sans\"}"))
            .andExpect(status().isBadRequest());
    }

    private void stubToken(String token, Long userId, Long orgId, Role role) {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken(token)).thenReturn(claims);
        when(jwtUtil.getUserId(claims)).thenReturn(userId);
        when(jwtUtil.getOrgId(claims)).thenReturn(orgId);
        when(jwtUtil.getRole(any(Claims.class))).thenReturn(role);
    }
}
