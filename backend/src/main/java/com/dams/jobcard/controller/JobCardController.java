package com.dams.jobcard.controller;

import com.dams.jobcard.dto.CloseClaimRequest;
import com.dams.jobcard.dto.JobCardCreateRequest;
import com.dams.jobcard.dto.JobCardPatchRequest;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.dto.RenewalRow;
import com.dams.jobcard.dto.WipRow;
import com.dams.jobcard.service.BoardService;
import com.dams.jobcard.service.ClaimCloseService;
import com.dams.jobcard.service.JobCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Job cards (cases). Any signed-in org user can create and read; a CASHIER's job card
 * always posts under their home branch. PATCH edits the invoice / dbm references freely and
 * the category / business status while the claim is still open.
 */
@RestController
@RequestMapping("/api/v1/job-cards")
@Tag(name = "Job Cards", description = "The case that ties a customer/vehicle to its documents")
@SecurityRequirement(name = "bearerAuth")
public class JobCardController {

    private final JobCardService jobCardService;
    private final ClaimCloseService claimCloseService;
    private final BoardService boardService;

    public JobCardController(JobCardService jobCardService, ClaimCloseService claimCloseService,
                             BoardService boardService) {
        this.jobCardService = jobCardService;
        this.claimCloseService = claimCloseService;
        this.boardService = boardService;
    }

    @PostMapping
    @Operation(summary = "Create a job card (inline customer/vehicle create supported)")
    @PreAuthorize("hasAnyAuthority('CASHIER','ACCOUNTANT','FINANCE_MANAGER')")
    public ResponseEntity<JobCardResponse> create(@Valid @RequestBody JobCardCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobCardService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a job card with derived fields (reference, is_claim, pending_amount)")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER')")
    public JobCardResponse get(@PathVariable Long id) {
        return jobCardService.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update invoice / dbm references, or category / business status while the claim is open")
    @PreAuthorize("hasAnyAuthority('CASHIER','ACCOUNTANT','FINANCE_MANAGER')")
    public JobCardResponse patch(@PathVariable Long id, @Valid @RequestBody JobCardPatchRequest request) {
        return jobCardService.patch(id, request);
    }

    @PostMapping("/{id}/close-claim")
    @Operation(summary = "Finance Manager: finalise a warranty / AMC / CG claim (immutable; optional final override)")
    @PreAuthorize("hasAuthority('FINANCE_MANAGER')")
    public JobCardResponse closeClaim(@PathVariable Long id, @Valid @RequestBody CloseClaimRequest request) {
        return claimCloseService.closeClaim(id, request);
    }

    @GetMapping("/board")
    @Operation(summary = "WIP floor board: open job cards oldest first (FEAT-50)")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','AUDITOR')")
    public java.util.List<WipRow> board() {
        return boardService.wip();
    }

    @GetMapping("/renewals")
    @Operation(summary = "Service/AMC dues: overdue + next 45 days (FEAT-39)")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER','AUDITOR')")
    public java.util.List<RenewalRow> renewals() {
        return boardService.renewals();
    }
}
