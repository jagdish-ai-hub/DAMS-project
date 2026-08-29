package com.dams.organization.controller;

import com.dams.organization.dto.OrgSettingsRequest;
import com.dams.organization.dto.OrgSettingsResponse;
import com.dams.organization.service.OrgSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own organization settings. Read: any signed-in org user. Write: OWNER. */
@RestController
@RequestMapping("/api/v1/organization")
@Tag(name = "Organization settings", description = "Owner: the org's own settings")
@SecurityRequirement(name = "bearerAuth")
public class OrgSettingsController {

    private final OrgSettingsService orgSettingsService;

    public OrgSettingsController(OrgSettingsService orgSettingsService) {
        this.orgSettingsService = orgSettingsService;
    }

    @GetMapping
    @Operation(summary = "Get the caller's organization settings")
    public OrgSettingsResponse get() {
        return orgSettingsService.get();
    }

    @PatchMapping
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(summary = "Update name / multi-branch cashier access")
    public OrgSettingsResponse update(@Valid @RequestBody OrgSettingsRequest request) {
        return orgSettingsService.update(request);
    }
}
