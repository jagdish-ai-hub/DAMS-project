package com.dams.cash;

import com.dams.auth.util.JwtUtil;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.controller.CashController;
import com.dams.cash.dto.CashDrawerResponse;
import com.dams.cash.service.CashCloseService;
import com.dams.config.ActingRoleFilter;
import com.dams.config.JwtConfig;
import com.dams.config.SecurityConfig;
import com.dams.config.TenantFilter;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserRoleGrantRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reopening a closed cash day is the Owner's alone (rev 60) — the wrong role never reaches the service. */
@WebMvcTest(CashController.class)
@Import({SecurityConfig.class, JwtConfig.class, TenantFilter.class, ActingRoleFilter.class})
class CashControllerSecurityTest {

    private static final String BODY = "{\"branchId\":3,\"closeDate\":\"2026-07-30\",\"reason\":\"Closed too early\"}";

    @MockBean private AppUserRepository actingUserRepo;
    @MockBean private UserRoleGrantRepository actingGrantRepo;
    @MockBean private BranchRepository actingBranchRepo;
    @MockBean private CashCloseService cashCloseService;
    @MockBean private JwtUtil jwtUtil;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reopenDay_okForOwner() throws Exception {
        when(cashCloseService.reopenDay(any())).thenReturn(mock(CashDrawerResponse.class));
        stubToken("owner-token", 5L, 1L, Role.OWNER);
        mockMvc.perform(post("/api/v1/cash/reopen-day").header("Authorization", "Bearer owner-token")
                .contentType("application/json").content(BODY))
            .andExpect(status().isOk());
    }

    @Test
    void reopenDay_forbiddenForCashierAccountantAndFinanceManager() throws Exception {
        for (Role role : new Role[]{Role.CASHIER, Role.ACCOUNTANT, Role.FINANCE_MANAGER}) {
            String token = role.name() + "-token";
            stubToken(token, 9L, 1L, role);
            mockMvc.perform(post("/api/v1/cash/reopen-day").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void reopenDay_requiresAReason() throws Exception {
        stubToken("owner-token", 5L, 1L, Role.OWNER);
        mockMvc.perform(post("/api/v1/cash/reopen-day").header("Authorization", "Bearer owner-token")
                .contentType("application/json")
                .content("{\"branchId\":3,\"closeDate\":\"2026-07-30\",\"reason\":\"  \"}"))
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
