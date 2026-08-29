package com.dams.auth;

import com.dams.auth.dto.AcceptInviteRequest;
import com.dams.auth.dto.LoginRequest;
import com.dams.auth.dto.LoginResponse;
import com.dams.auth.service.AuthService;
import com.dams.auth.util.JwtUtil;
import com.dams.common.exception.DamsException;
import com.dams.config.JwtConfig;
import com.dams.config.SecurityConfig;
import com.dams.config.TenantFilter;
import com.dams.user.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.dams.auth.controller.AuthController;

/**
 * Slice tests for the AuthController endpoints.
 * Test names describe the behaviour being proven — see AGENT.md coding standards.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtConfig.class, TenantFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtil jwtUtil;

    // --- Login ---

    @Test
    void login_returnsTokenAndRole_whenCredentialsValid() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@dams.local");
        req.setPassword("admin123");

        LoginResponse resp = new LoginResponse("access-token", Role.SUPER_ADMIN, null, null, "Super Admin");
        when(authService.login(any(LoginRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
            .andExpect(jsonPath("$.name").value("Super Admin"));
    }

    @Test
    void login_returns401_whenPasswordIsWrong() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("admin@dams.local");
        req.setPassword("wrongpassword");

        when(authService.login(any(LoginRequest.class)))
            .thenThrow(DamsException.badRequest("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_returns400_whenEmailMissing() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setPassword("admin123");
        // email intentionally omitted

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    // --- Accept invite ---

    @Test
    void acceptInvite_returnsToken_whenTokenValid() throws Exception {
        AcceptInviteRequest req = new AcceptInviteRequest();
        req.setToken("valid-uuid-token");
        req.setPassword("newpassword1");

        LoginResponse resp = new LoginResponse("access-token", Role.OWNER, 1L, null, "Test Owner");
        when(authService.acceptInvite(any(AcceptInviteRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/accept-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andExpect(jsonPath("$.orgId").value(1));
    }

    @Test
    void acceptInvite_returns400_whenTokenExpired() throws Exception {
        AcceptInviteRequest req = new AcceptInviteRequest();
        req.setToken("expired-token");
        req.setPassword("newpassword1");

        when(authService.acceptInvite(any(AcceptInviteRequest.class)))
            .thenThrow(DamsException.badRequest("Invite token for AppUser 5 has expired"));

        mockMvc.perform(post("/api/v1/auth/accept-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invite token for AppUser 5 has expired"));
    }

    @Test
    void acceptInvite_returns400_whenTokenNotFound() throws Exception {
        AcceptInviteRequest req = new AcceptInviteRequest();
        req.setToken("nonexistent-token");
        req.setPassword("newpassword1");

        when(authService.acceptInvite(any(AcceptInviteRequest.class)))
            .thenThrow(DamsException.badRequest("Invite token not found or already used"));

        mockMvc.perform(post("/api/v1/auth/accept-invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invite token not found or already used"));
    }
}
