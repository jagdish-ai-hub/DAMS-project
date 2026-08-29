package com.dams.branch.controller;

import com.dams.branch.dto.BranchRequest;
import com.dams.branch.dto.BranchResponse;
import com.dams.branch.service.BranchService;
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
 * Branch management. Reads are open to any authenticated org user (dropdowns need them);
 * writes are OWNER-only. All actions are org-scoped by the service.
 */
@RestController
@RequestMapping("/api/v1/branches")
@Tag(name = "Branches", description = "Owner: manage dealership branches")
@SecurityRequirement(name = "bearerAuth")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    @Operation(summary = "List branches in the caller's organization")
    public List<BranchResponse> list() {
        return branchService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one branch")
    public BranchResponse get(@PathVariable Long id) {
        return branchService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(summary = "Create a branch")
    public ResponseEntity<BranchResponse> create(@Valid @RequestBody BranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(branchService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(summary = "Update a branch (name / code / active)")
    public BranchResponse update(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return branchService.update(id, request);
    }
}
