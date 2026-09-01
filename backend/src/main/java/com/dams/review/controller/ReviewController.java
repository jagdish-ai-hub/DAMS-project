package com.dams.review.controller;

import com.dams.cash.dto.CashDocumentResponse;
import com.dams.expense.dto.ExpenseDocumentResponse;
import com.dams.receive.dto.ReceiveDocumentResponse;
import com.dams.review.dto.FmQueue;
import com.dams.review.dto.LineOverrideRequest;
import com.dams.review.dto.QueryRequest;
import com.dams.review.dto.RejectRequest;
import com.dams.review.dto.ReviewQueueItem;
import com.dams.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The Accountant review step (Stage 7). Two read endpoints for the queue, and the
 * per-document actions on the {@code /receipts} and {@code /expenses} paths (AGENT.md REST
 * naming: {@code POST /receipts/{id}/verify}). Every action is ACCOUNTANT-only, branch-scoped,
 * and refused on a document the caller created or last modified (maker-checker) — enforced
 * in {@link com.dams.review.service.ReviewGuard}.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Review", description = "Accountant verify / query / reject / override / close")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // ---- queue ----

    @GetMapping("/review/receipts")
    @Operation(summary = "Receipts awaiting this accountant's review (SUBMITTED, in their branches)")
    public List<ReviewQueueItem> receiptQueue() {
        return reviewService.receiptQueue();
    }

    @GetMapping("/review/expenses")
    @Operation(summary = "Expenses awaiting this accountant's review (SUBMITTED, in their branches)")
    public List<ReviewQueueItem> expenseQueue() {
        return reviewService.expenseQueue();
    }

    @GetMapping("/review/fm/receipts")
    @Operation(summary = "Finance Manager receipt queue — awaiting approval, open claims, recently closed")
    public FmQueue fmReceiptQueue() {
        return reviewService.fmReceiptQueue();
    }

    @GetMapping("/review/fm/expenses")
    @Operation(summary = "Finance Manager expense queue — expenses awaiting final approval")
    public FmQueue fmExpenseQueue() {
        return reviewService.fmExpenseQueue();
    }

    @GetMapping("/review/cash")
    @Operation(summary = "Cash movements awaiting this accountant's review (SUBMITTED, in their branches)")
    public List<ReviewQueueItem> cashQueue() {
        return reviewService.cashQueue();
    }

    @GetMapping("/review/fm/cash")
    @Operation(summary = "Finance Manager cash queue — movements awaiting final approval")
    public FmQueue fmCashQueue() {
        return reviewService.fmCashQueue();
    }

    // ---- receipt actions ----

    @PostMapping("/receipts/{id}/verify")
    @Operation(summary = "Verify a submitted receipt — moves it to Finance Manager approval")
    public ReceiveDocumentResponse verifyReceipt(@PathVariable Long id) {
        return reviewService.verifyReceipt(id);
    }

    @PostMapping("/receipts/{id}/query")
    @Operation(summary = "Query a receipt back to the cashier — Accountant (submitted) or FM (verified)")
    public ReceiveDocumentResponse queryReceipt(@PathVariable Long id, @Valid @RequestBody QueryRequest request) {
        return reviewService.queryReceipt(id, request.note().trim());
    }

    @PostMapping("/receipts/{id}/reject")
    @Operation(summary = "Reject a receipt with a reason — Accountant (submitted) or FM (verified)")
    public ReceiveDocumentResponse rejectReceipt(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return reviewService.rejectReceipt(id, request.reason().trim());
    }

    @PostMapping("/receipts/{id}/lines/{lineNo}/override")
    @Operation(summary = "Override one settlement line's amount (provisional — reason required)")
    public ReceiveDocumentResponse overrideReceiptLine(@PathVariable Long id, @PathVariable Integer lineNo,
                                                       @Valid @RequestBody LineOverrideRequest request) {
        return reviewService.overrideReceiptLine(id, lineNo, request.amount(), request.reason().trim());
    }

    @PostMapping("/receipts/{id}/approve")
    @Operation(summary = "Finance Manager: give a verified receipt final approval")
    public ReceiveDocumentResponse approveReceipt(@PathVariable Long id) {
        return reviewService.approveReceipt(id);
    }

    // ---- expense actions ----

    @PostMapping("/expenses/{id}/verify")
    @Operation(summary = "Verify a submitted expense — moves it to Finance Manager approval")
    public ExpenseDocumentResponse verifyExpense(@PathVariable Long id) {
        return reviewService.verifyExpense(id);
    }

    @PostMapping("/expenses/{id}/query")
    @Operation(summary = "Query an expense back to the cashier — Accountant (submitted) or FM (verified)")
    public ExpenseDocumentResponse queryExpense(@PathVariable Long id, @Valid @RequestBody QueryRequest request) {
        return reviewService.queryExpense(id, request.note().trim());
    }

    @PostMapping("/expenses/{id}/reject")
    @Operation(summary = "Reject an expense with a reason — Accountant (submitted) or FM (verified)")
    public ExpenseDocumentResponse rejectExpense(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return reviewService.rejectExpense(id, request.reason().trim());
    }

    @PostMapping("/expenses/{id}/lines/{lineNo}/override")
    @Operation(summary = "Override one expense line's amount (provisional — reason required)")
    public ExpenseDocumentResponse overrideExpenseLine(@PathVariable Long id, @PathVariable Integer lineNo,
                                                       @Valid @RequestBody LineOverrideRequest request) {
        return reviewService.overrideExpenseLine(id, lineNo, request.amount(), request.reason().trim());
    }

    @PostMapping("/expenses/{id}/close")
    @Operation(summary = "Close an expense (VERIFIED/APPROVED; an over-limit expense needs FM approval first)")
    public ExpenseDocumentResponse closeExpense(@PathVariable Long id) {
        return reviewService.closeExpense(id);
    }

    @PostMapping("/expenses/{id}/approve")
    @Operation(summary = "Finance Manager: give a verified expense final approval")
    public ExpenseDocumentResponse approveExpense(@PathVariable Long id) {
        return reviewService.approveExpense(id);
    }

    // ---- cash actions ----

    @PostMapping("/cash-documents/{id}/verify")
    @Operation(summary = "Verify a submitted cash movement — moves it to Finance Manager approval")
    public CashDocumentResponse verifyCash(@PathVariable Long id) {
        return reviewService.verifyCash(id);
    }

    @PostMapping("/cash-documents/{id}/approve")
    @Operation(summary = "Finance Manager: give a verified cash movement final approval")
    public CashDocumentResponse approveCash(@PathVariable Long id) {
        return reviewService.approveCash(id);
    }

    @PostMapping("/cash-documents/{id}/query")
    @Operation(summary = "Query a cash movement back to the cashier — Accountant (submitted) or FM (verified)")
    public CashDocumentResponse queryCash(@PathVariable Long id, @Valid @RequestBody QueryRequest request) {
        return reviewService.queryCash(id, request.note().trim());
    }

    @PostMapping("/cash-documents/{id}/reject")
    @Operation(summary = "Reject a cash movement with a reason — Accountant (submitted) or FM (verified)")
    public CashDocumentResponse rejectCash(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return reviewService.rejectCash(id, request.reason().trim());
    }
}
