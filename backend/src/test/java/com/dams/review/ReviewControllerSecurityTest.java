package com.dams.review;

import com.dams.auth.util.JwtUtil;
import com.dams.config.JwtConfig;
import com.dams.config.SecurityConfig;
import com.dams.config.TenantFilter;
import com.dams.expense.dto.ExpenseDocumentResponse;
import com.dams.export.controller.ExportController;
import com.dams.export.service.ExportService;
import com.dams.jobcard.controller.JobCardController;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.service.ClaimCloseService;
import com.dams.jobcard.service.JobCardService;
import com.dams.receive.dto.ReceiveDocumentResponse;
import com.dams.review.controller.ReviewController;
import com.dams.review.dto.FmQueue;
import com.dams.review.service.ReviewService;
import com.dams.user.entity.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer role gates mirror the service guards: the service would refuse anyway,
 * but the wrong role must never even reach it. One proof per gate family.
 */
@WebMvcTest({ReviewController.class, JobCardController.class, ExportController.class})
@Import({SecurityConfig.class, JwtConfig.class, TenantFilter.class})
class ReviewControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;
    @MockBean
    private JobCardService jobCardService;
    @MockBean
    private ClaimCloseService claimCloseService;
    @MockBean
    private ExportService exportService;
    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void reviewQueue_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/review/receipts"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void reviewQueue_okForAccountant_forbiddenForOwner() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(reviewService.receiptQueue()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/review/receipts").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isOk());

        stubToken("owner-token", 5L, 1L, Role.OWNER);
        mockMvc.perform(get("/api/v1/review/receipts").header("Authorization", "Bearer owner-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void fmQueue_okForFinanceManager_forbiddenForAccountant() throws Exception {
        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        when(reviewService.fmReceiptQueue()).thenReturn(mock(FmQueue.class));
        mockMvc.perform(get("/api/v1/review/fm/receipts").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isOk());

        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        mockMvc.perform(get("/api/v1/review/fm/receipts").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void verifyReceipt_okForAccountant_forbiddenForOwner() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(reviewService.verifyReceipt(1L)).thenReturn(mock(ReceiveDocumentResponse.class));
        mockMvc.perform(post("/api/v1/receipts/1/verify").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isOk());

        stubToken("owner-token", 5L, 1L, Role.OWNER);
        mockMvc.perform(post("/api/v1/receipts/1/verify").header("Authorization", "Bearer owner-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void approveReceipt_okForFinanceManager_forbiddenForAccountant() throws Exception {
        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        when(reviewService.approveReceipt(1L)).thenReturn(mock(ReceiveDocumentResponse.class));
        mockMvc.perform(post("/api/v1/receipts/1/approve").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isOk());

        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        mockMvc.perform(post("/api/v1/receipts/1/approve").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void closeExpense_okForAccountant_forbiddenForFinanceManager() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(reviewService.closeExpense(1L)).thenReturn(mock(ExpenseDocumentResponse.class));
        mockMvc.perform(post("/api/v1/expenses/1/close").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isOk());

        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(post("/api/v1/expenses/1/close").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void queryReceipt_forbiddenForOwner_okForFinanceManager() throws Exception {
        stubToken("owner-token", 5L, 1L, Role.OWNER);
        mockMvc.perform(post("/api/v1/receipts/1/query").header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"why?\"}"))
            .andExpect(status().isForbidden());

        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        when(reviewService.queryReceipt(1L, "why?")).thenReturn(mock(ReceiveDocumentResponse.class));
        mockMvc.perform(post("/api/v1/receipts/1/query").header("Authorization", "Bearer fm-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"why?\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void overrideAndBulk_forbiddenForOwner() throws Exception {
        stubToken("owner-token", 5L, 1L, Role.OWNER);
        mockMvc.perform(post("/api/v1/receipts/1/lines/1/override").header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10,\"reason\":\"r\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/receipts/bulk-verify").header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void jobCardGet_okForOwner_patchForbiddenForOwner() throws Exception {
        stubToken("owner-token", 5L, 1L, Role.OWNER);
        when(jobCardService.get(9L)).thenReturn(mock(JobCardResponse.class));
        mockMvc.perform(get("/api/v1/job-cards/9").header("Authorization", "Bearer owner-token"))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/job-cards/9").header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void closeClaim_okForFinanceManager_forbiddenForAccountant() throws Exception {
        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        when(claimCloseService.closeClaim(any(), any())).thenReturn(mock(JobCardResponse.class));
        mockMvc.perform(post("/api/v1/job-cards/9/close-claim").header("Authorization", "Bearer fm-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"finalAmount\":100}"))
            .andExpect(status().isOk());

        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        mockMvc.perform(post("/api/v1/job-cards/9/close-claim").header("Authorization", "Bearer acct-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"finalAmount\":100}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void exportReceipts_okForOwner_forbiddenForCashier() throws Exception {
        stubToken("owner-token", 5L, 1L, Role.OWNER);
        when(exportService.exportReceiptsCsv(any(), any(), any())).thenReturn(new byte[0]);
        mockMvc.perform(get("/api/v1/export/receipts").header("Authorization", "Bearer owner-token"))
            .andExpect(status().isOk());

        stubToken("cashier-token", 7L, 1L, Role.CASHIER);
        mockMvc.perform(get("/api/v1/export/receipts").header("Authorization", "Bearer cashier-token"))
            .andExpect(status().isForbidden());
    }

    private void stubToken(String token, Long userId, Long orgId, Role role) {
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseToken(token)).thenReturn(claims);
        when(jwtUtil.getUserId(claims)).thenReturn(userId);
        when(jwtUtil.getOrgId(claims)).thenReturn(orgId);
        when(jwtUtil.getRole(any(Claims.class))).thenReturn(role);
    }
}
