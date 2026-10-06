package com.dams.review;

import com.dams.auth.util.JwtUtil;
import com.dams.config.JwtConfig;
import com.dams.config.SecurityConfig;
import com.dams.config.ActingRoleFilter;
import com.dams.config.TenantFilter;
import com.dams.branch.repository.BranchRepository;
import com.dams.user.repository.AppUserRepository;
import com.dams.user.repository.UserRoleGrantRepository;
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
import static org.mockito.ArgumentMatchers.eq;
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
@Import({SecurityConfig.class, JwtConfig.class, TenantFilter.class, ActingRoleFilter.class})
class ReviewControllerSecurityTest {

    // ActingRoleFilter (rev 55) is part of the security chain; its collaborators are mocked here.
    @MockBean private AppUserRepository actingUserRepo;
    @MockBean private UserRoleGrantRepository actingGrantRepo;
    @MockBean private BranchRepository actingBranchRepo;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;
    @MockBean
    private com.dams.review.service.ReviewerLookupService reviewerLookupService;
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
    void reviewers_okForAccountantAndFinanceManager_forbiddenForCashierAndOwner() throws Exception {
        when(reviewerLookupService.reviewersFor(eq(1L), eq(Role.ACCOUNTANT), any())).thenReturn(List.of());
        for (Role ok : List.of(Role.ACCOUNTANT, Role.FINANCE_MANAGER)) {
            stubToken("t-" + ok, 6L, 1L, ok);
            mockMvc.perform(get("/api/v1/review/reviewers?branchId=1&step=ACCOUNTANT&exclude=2&exclude=3")
                    .header("Authorization", "Bearer t-" + ok))
                .andExpect(status().isOk());
        }
        for (Role no : List.of(Role.CASHIER, Role.OWNER)) {
            stubToken("t-" + no, 6L, 1L, no);
            mockMvc.perform(get("/api/v1/review/reviewers?branchId=1&step=ACCOUNTANT")
                    .header("Authorization", "Bearer t-" + no))
                .andExpect(status().isForbidden());
        }
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
    void expensePreApproval_isFinanceManagerOnly() throws Exception {
        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        when(reviewService.preApproveExpense(1L)).thenReturn(mock(ExpenseDocumentResponse.class));
        mockMvc.perform(post("/api/v1/expenses/1/pre-approve").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/review/fm/expense-requests").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isOk());

        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        mockMvc.perform(post("/api/v1/expenses/1/pre-approve").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/review/fm/expense-requests").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isForbidden());

        stubToken("cashier-token", 7L, 1L, Role.CASHIER);
        mockMvc.perform(post("/api/v1/expenses/1/pre-approve").header("Authorization", "Bearer cashier-token"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/expenses/1/query-approval").header("Authorization", "Bearer cashier-token")
                .contentType("application/json").content("{\"note\":\"x\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void closeExpenseClaim_isFinanceManagerOnly() throws Exception {
        String body = "{\"finalAmount\":4200,\"reason\":\"OEM part payment\"}";
        when(reviewService.closeExpenseClaim(eq(1L), any(), any())).thenReturn(mock(ExpenseDocumentResponse.class));
        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(post("/api/v1/expenses/1/close-claim").header("Authorization", "Bearer fm-token")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        for (Role role : new Role[]{Role.ACCOUNTANT, Role.CASHIER, Role.OWNER}) {
            String token = role.name() + "-token";
            stubToken(token, 9L, 1L, role);
            mockMvc.perform(post("/api/v1/expenses/1/close-claim").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void closeExpenseClaim_rejectsANegativeFinalAmount() throws Exception {
        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(post("/api/v1/expenses/1/close-claim").header("Authorization", "Bearer fm-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"finalAmount\":-1}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void ownerExpenseList_isOwnerOnly() throws Exception {
        when(reviewService.ownerExpenseList()).thenReturn(List.of());
        stubToken("owner-token", 5L, 1L, Role.OWNER);
        mockMvc.perform(get("/api/v1/review/owner/expenses").header("Authorization", "Bearer owner-token"))
            .andExpect(status().isOk());

        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(get("/api/v1/review/owner/expenses").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isForbidden());
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        mockMvc.perform(get("/api/v1/review/owner/expenses").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isForbidden());
        stubToken("cashier-token", 7L, 1L, Role.CASHIER);
        mockMvc.perform(get("/api/v1/review/owner/expenses").header("Authorization", "Bearer cashier-token"))
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
    void overrideInvoiceAmount_okForAccountant_forbiddenForFinanceManager() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(reviewService.overrideInvoiceAmount(any(), any(), any())).thenReturn(mock(ReceiveDocumentResponse.class));
        mockMvc.perform(post("/api/v1/receipts/1/override-invoice-amount").header("Authorization", "Bearer acct-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10,\"reason\":\"r\"}"))
            .andExpect(status().isOk());

        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(post("/api/v1/receipts/1/override-invoice-amount").header("Authorization", "Bearer fm-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10,\"reason\":\"r\"}"))
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

    @Test
    void exportReceiptsByIds_okForAccountant_forbiddenForCashier() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(exportService.exportReceiptsCsvByIds(any())).thenReturn(new byte[0]);
        mockMvc.perform(get("/api/v1/export/receipts/by-id?ids=1,2").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isOk());

        stubToken("cashier-token", 7L, 1L, Role.CASHIER);
        mockMvc.perform(get("/api/v1/export/receipts/by-id?ids=1,2").header("Authorization", "Bearer cashier-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void directApproveReceipt_okForAccountant_forbiddenForFinanceManager() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(reviewService.directApproveReceipt(any())).thenReturn(mock(ReceiveDocumentResponse.class));
        mockMvc.perform(post("/api/v1/receipts/1/direct-approve").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isOk());

        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(post("/api/v1/receipts/1/direct-approve").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void directApproveEligibleReceipts_okForAccountant_forbiddenForFinanceManager() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(reviewService.directApproveEligibleReceipts()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/review/receipts/direct-approve-eligible").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isOk());

        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(get("/api/v1/review/receipts/direct-approve-eligible").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void resubmitReceiptToFm_okForAccountant_forbiddenForFinanceManager() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(reviewService.resubmitReceiptToFm(1L)).thenReturn(mock(ReceiveDocumentResponse.class));
        mockMvc.perform(post("/api/v1/receipts/1/resubmit-to-fm").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isOk());

        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(post("/api/v1/receipts/1/resubmit-to-fm").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void resubmitExpenseToFm_okForAccountant_forbiddenForFinanceManager() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(reviewService.resubmitExpenseToFm(1L)).thenReturn(mock(ExpenseDocumentResponse.class));
        mockMvc.perform(post("/api/v1/expenses/1/resubmit-to-fm").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isOk());

        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(post("/api/v1/expenses/1/resubmit-to-fm").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void resubmitCashToFm_okForAccountant_forbiddenForFinanceManager() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(reviewService.resubmitCashToFm(1L)).thenReturn(mock(com.dams.cash.dto.CashDocumentResponse.class));
        mockMvc.perform(post("/api/v1/cash-documents/1/resubmit-to-fm").header("Authorization", "Bearer acct-token"))
            .andExpect(status().isOk());

        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(post("/api/v1/cash-documents/1/resubmit-to-fm").header("Authorization", "Bearer fm-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void bulkDirectApproveReceipts_okForAccountant_forbiddenForFinanceManager() throws Exception {
        stubToken("acct-token", 6L, 1L, Role.ACCOUNTANT);
        when(reviewService.bulkDirectApproveReceipts(any()))
            .thenReturn(new com.dams.review.dto.BulkApproveResponse(0, List.of(), List.of()));
        mockMvc.perform(post("/api/v1/receipts/direct-approve").header("Authorization", "Bearer acct-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1]}"))
            .andExpect(status().isOk());

        stubToken("fm-token", 8L, 1L, Role.FINANCE_MANAGER);
        mockMvc.perform(post("/api/v1/receipts/direct-approve").header("Authorization", "Bearer fm-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1]}"))
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
