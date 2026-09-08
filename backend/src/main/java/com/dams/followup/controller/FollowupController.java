package com.dams.followup.controller;

import com.dams.followup.dto.CreateFollowupRequest;
import com.dams.followup.dto.DefaulterRow;
import com.dams.followup.dto.FollowupResponse;
import com.dams.followup.service.FollowupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Collection follow-ups (FEAT-35). The counter logs the customer's promise at
 * billing time; the accountant herds the list; the owner watches defaulters.
 * Reads are branch-scoped; writes are open to every transacting role except
 * the read-only auditor.
 */
@RestController
@RequestMapping("/api/v1/followups")
@Tag(name = "Collection follow-ups", description = "FEAT-35: dues with owners and dates")
@SecurityRequirement(name = "bearerAuth")
public class FollowupController {

    private final FollowupService followupService;

    public FollowupController(FollowupService followupService) {
        this.followupService = followupService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER','AUDITOR')")
    @Operation(summary = "Live follow-ups, oldest due first (?overdueOnly=true for the chase list)")
    public List<FollowupResponse> list(
            @RequestParam(name = "overdueOnly", defaultValue = "false") boolean overdueOnly) {
        return followupService.list(overdueOnly);
    }

    @GetMapping("/defaulters")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','AUDITOR')")
    @Operation(summary = "Customers ranked by outstanding across live follow-ups")
    public List<DefaulterRow> defaulters() {
        return followupService.defaulters();
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER')")
    @Operation(summary = "Open (or re-promise) a follow-up on a receive document")
    public FollowupResponse open(@Valid @RequestBody CreateFollowupRequest request) {
        return followupService.open(request);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER')")
    @Operation(summary = "Close a follow-up once the due is collected")
    public FollowupResponse close(@PathVariable Long id) {
        return followupService.close(id);
    }
}
