package com.dams.estimate.controller;

import com.dams.estimate.dto.CreateEstimateRequest;
import com.dams.estimate.dto.EstimateResponse;
import com.dams.estimate.service.EstimateService;
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
 * Estimates (FEAT-48). The counter drafts the quote, the FM approves big
 * numbers; re-quoting supersedes. Variance vs the final invoice shows on
 * every response once the job is billed.
 */
@RestController
@RequestMapping("/api/v1/estimates")
@Tag(name = "Estimates", description = "FEAT-48: agreed quote before work starts")
@SecurityRequirement(name = "bearerAuth")
public class EstimateController {

    private final EstimateService estimateService;

    public EstimateController(EstimateService estimateService) {
        this.estimateService = estimateService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER','AUDITOR')")
    @Operation(summary = "Estimate history for one job card (?jobCardId=, newest first)")
    public List<EstimateResponse> forJobCard(@RequestParam("jobCardId") Long jobCardId) {
        return estimateService.forJobCard(jobCardId);
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('CASHIER','ACCOUNTANT','FINANCE_MANAGER')")
    @Operation(summary = "Draft an estimate (supersedes any live one on the job card)")
    public ResponseEntity<EstimateResponse> create(@Valid @RequestBody CreateEstimateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(estimateService.create(request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('FINANCE_MANAGER','OWNER')")
    @Operation(summary = "Approve an estimate")
    public EstimateResponse approve(@PathVariable Long id,
                                    @RequestParam(name = "note", required = false) String note) {
        return estimateService.decide(id, true, note);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('FINANCE_MANAGER','OWNER')")
    @Operation(summary = "Reject an estimate with a reason")
    public EstimateResponse reject(@PathVariable Long id,
                                   @RequestParam(name = "note", required = false) String note) {
        return estimateService.decide(id, false, note);
    }
}
