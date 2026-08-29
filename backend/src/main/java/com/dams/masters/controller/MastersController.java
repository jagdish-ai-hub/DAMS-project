package com.dams.masters.controller;

import com.dams.masters.MasterType;
import com.dams.masters.dto.MasterRequest;
import com.dams.masters.dto.MasterResponse;
import com.dams.masters.service.MastersService;
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
 * Owner-editable masters, one URL family for all eight lists:
 *   /api/v1/masters/{type}         where {type} is the MasterType slug
 * Reads are open to any authenticated org user (the app's dropdowns need them);
 * writes are OWNER-only. Rows are deactivated, never deleted.
 */
@RestController
@RequestMapping("/api/v1/masters/{type}")
@Tag(name = "Masters", description = "Owner: manage every lookup list the app uses")
@SecurityRequirement(name = "bearerAuth")
public class MastersController {

    private final MastersService mastersService;

    public MastersController(MastersService mastersService) {
        this.mastersService = mastersService;
    }

    @GetMapping
    @Operation(summary = "List a master (optionally ?expenseCategoryId= for sub-categories)")
    public List<MasterResponse> list(@PathVariable String type,
                                     @RequestParam(required = false) Long expenseCategoryId) {
        return mastersService.list(MasterType.fromSlug(type), expenseCategoryId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one master row")
    public MasterResponse get(@PathVariable String type, @PathVariable Long id) {
        return mastersService.get(MasterType.fromSlug(type), id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(summary = "Add a row to a master")
    public ResponseEntity<MasterResponse> create(@PathVariable String type,
                                                 @Valid @RequestBody MasterRequest request) {
        MasterResponse created = mastersService.create(MasterType.fromSlug(type), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(summary = "Update a master row (name / active / sortOrder / type-specific fields)")
    public MasterResponse update(@PathVariable String type,
                                 @PathVariable Long id,
                                 @Valid @RequestBody MasterRequest request) {
        return mastersService.update(MasterType.fromSlug(type), id, request);
    }
}
