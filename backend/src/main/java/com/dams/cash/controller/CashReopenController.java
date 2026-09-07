package com.dams.cash.controller;

import com.dams.cash.dto.CreateReopenRequest;
import com.dams.cash.dto.RejectReopenRequest;
import com.dams.cash.dto.ReopenRequestResponse;
import com.dams.cash.entity.ReopenRequestStatus;
import com.dams.cash.service.CashReopenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Cash-close reopen requests: the cashier asks (reason mandatory), a Finance Manager
 * approves (the close row is removed so the day can be re-closed) or rejects with a
 * reason. There is deliberately no silent reopen — every reopen leaves an audit trail.
 */
@RestController
@RequestMapping("/api/v1/cash/reopen-requests")
@Tag(name = "Cash Reopen", description = "Request / approve reopening a locked cash day")
@SecurityRequirement(name = "bearerAuth")
public class CashReopenController {

    private final CashReopenService reopenService;

    public CashReopenController(CashReopenService reopenService) {
        this.reopenService = reopenService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CASHIER')")
    @Operation(summary = "CASHIER — request a locked day to be reopened (home branch)")
    public ResponseEntity<ReopenRequestResponse> request(@Valid @RequestBody CreateReopenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reopenService.request(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ACCOUNTANT','FINANCE_MANAGER','OWNER')")
    @Operation(summary = "List reopen requests in the caller's branch scope (optional ?branchId ?status)")
    public List<ReopenRequestResponse> list(
        @RequestParam(name = "branchId", required = false) Long branchId,
        @RequestParam(name = "status", required = false) ReopenRequestStatus status) {
        return reopenService.list(branchId, status);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('FINANCE_MANAGER')")
    @Operation(summary = "FM — approve a reopen request (removes the day close so it can be re-closed)")
    public ReopenRequestResponse approve(@PathVariable Long id) {
        return reopenService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('FINANCE_MANAGER')")
    @Operation(summary = "FM — reject a reopen request with a reason (the lock stays)")
    public ReopenRequestResponse reject(@PathVariable Long id,
                                        @Valid @RequestBody RejectReopenRequest request) {
        return reopenService.reject(id, request.getReason());
    }
}
