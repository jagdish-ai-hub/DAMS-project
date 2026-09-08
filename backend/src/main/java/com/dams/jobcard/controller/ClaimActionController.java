package com.dams.jobcard.controller;

import com.dams.jobcard.dto.ClaimActionResponse;
import com.dams.jobcard.dto.CreateClaimActionRequest;
import com.dams.jobcard.service.ClaimActionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Claim next-actions (FEAT-38). Writes are FM/Owner — the chase belongs to
 * the claim owner. Reads follow branch scope (FM/Owner are org-wide anyway).
 */
@RestController
@RequestMapping("/api/v1/claim-actions")
@Tag(name = "Claim actions", description = "FEAT-38: next step on every open claim")
@SecurityRequirement(name = "bearerAuth")
public class ClaimActionController {

    private final ClaimActionService claimActionService;

    public ClaimActionController(ClaimActionService claimActionService) {
        this.claimActionService = claimActionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','AUDITOR')")
    @Operation(summary = "All open claim actions, most overdue first")
    public List<ClaimActionResponse> openActions() {
        return claimActionService.openActions();
    }

    @GetMapping("/job-card/{jobCardId}")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','AUDITOR')")
    @Operation(summary = "Action history for one claim job card")
    public List<ClaimActionResponse> forJobCard(@PathVariable Long jobCardId) {
        return claimActionService.forJobCard(jobCardId);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "Record the next step on a claim")
    public ClaimActionResponse create(@Valid @RequestBody CreateClaimActionRequest request) {
        return claimActionService.create(request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "Mark a claim action done (history is kept)")
    public ClaimActionResponse complete(@PathVariable Long id) {
        return claimActionService.complete(id);
    }
}
