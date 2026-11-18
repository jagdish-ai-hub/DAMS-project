package com.dams.invoiceextract;

import com.dams.auth.util.JwtUtil;
import com.dams.branch.repository.BranchRepository;
import com.dams.config.ActingRoleFilter;
import com.dams.config.JwtConfig;
import com.dams.config.SecurityConfig;
import com.dams.config.TenantFilter;
import com.dams.invoiceextract.controller.InvoiceExtractController;
import com.dams.invoiceextract.dto.InvoiceExtractResponse;
import com.dams.invoiceextract.service.InvoiceExtractService;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserRoleGrantRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The invoice-extract test tool belongs to the Finance Manager (rev 98). */
@WebMvcTest(InvoiceExtractController.class)
@Import({SecurityConfig.class, JwtConfig.class, TenantFilter.class, ActingRoleFilter.class})
class InvoiceExtractControllerSecurityTest {

    @MockBean private AppUserRepository actingUserRepo;
    @MockBean private UserRoleGrantRepository actingGrantRepo;
    @MockBean private BranchRepository actingBranchRepo;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private InvoiceExtractService service;

    @Autowired private MockMvc mockMvc;

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "inv.pdf", "application/pdf", "%PDF-1.4".getBytes());
    }

    @Test
    void noToken_is401() throws Exception {
        mockMvc.perform(multipart("/api/v1/invoice-extract").file(pdf())).andExpect(status().isUnauthorized());
    }

    @Test
    void aCashier_is403() throws Exception {
        stubToken("cashier-token", 7L, 1L, Role.CASHIER);

        mockMvc.perform(multipart("/api/v1/invoice-extract").file(pdf()).header("Authorization", "Bearer cashier-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void anAccountant_is403() throws Exception {
        stubToken("acc-token", 8L, 1L, Role.ACCOUNTANT);

        mockMvc.perform(multipart("/api/v1/invoice-extract").file(pdf()).header("Authorization", "Bearer acc-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void aFinanceManager_gets200_withTheExtractedData() throws Exception {
        stubToken("fm-token", 9L, 1L, Role.FINANCE_MANAGER);
        when(service.extract(any())).thenReturn(new InvoiceExtractResponse("inv.pdf", "TAX_INVOICE", "Tax invoice (IGST)", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0));

        mockMvc.perform(multipart("/api/v1/invoice-extract").file(pdf()).header("Authorization", "Bearer fm-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentType").value("TAX_INVOICE"));
    }

    private void stubToken(String token, Long userId, Long orgId, Role role) {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken(token)).thenReturn(claims);
        when(jwtUtil.getUserId(claims)).thenReturn(userId);
        when(jwtUtil.getOrgId(claims)).thenReturn(orgId);
        when(jwtUtil.getRole(any(Claims.class))).thenReturn(role);
    }
}
