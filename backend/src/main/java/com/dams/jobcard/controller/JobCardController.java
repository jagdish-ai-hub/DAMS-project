package com.dams.jobcard.controller;

import com.dams.jobcard.dto.CloseClaimRequest;
import com.dams.jobcard.dto.JobCardCreateRequest;
import com.dams.jobcard.dto.JobCardPatchRequest;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.service.ClaimCloseService;
import com.dams.jobcard.service.JobCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public JobCardController(JobCardService jobCardService, ClaimCloseService claimCloseService) {
        this.jobCardService = jobCardService;
        this.claimCloseService = claimCloseService;
    }

    @PostMapping
    @Operation(summary = "Create a job card (inline customer/vehicle create supported)")
    public ResponseEntity<JobCardResponse> create(@Valid @RequestBody JobCardCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobCardService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a job card with derived fields (reference, is_claim, pending_amount)")
    public JobCardResponse get(@PathVariable Long id) {
        return jobCardService.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update invoice / dbm references, or category / business status while the claim is open")
    public JobCardResponse patch(@PathVariable Long id, @Valid @RequestBody JobCardPatchRequest request) {
        return jobCardService.patch(id, request);
    }

    @PostMapping("/{id}/close-claim")
    @Operation(summary = "Finance Manager: finalise a warranty / AMC / CG claim (immutable; optional final override)")
    public JobCardResponse closeClaim(@PathVariable Long id, @Valid @RequestBody CloseClaimRequest request) {
        return claimCloseService.closeClaim(id, request);
    }
}
