package com.dams.admin.controller;

import com.dams.admin.service.AdminOrgService;
import com.dams.organization.entity.Organization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Super Admin — cross-org endpoints. Requires role SUPER_ADMIN.
 * Intentionally in its own controller/service package so it's visually obvious
 * these are the ONLY cross-org queries. See AGENT.md — Multi-tenancy.
 */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@Tag(name = "Admin — Organizations", description = "Super Admin: manage organizations and invite Owners")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class AdminOrgController {

    private final AdminOrgService adminOrgService;

    public AdminOrgController(AdminOrgService adminOrgService) {
        this.adminOrgService = adminOrgService;
    }

    @GetMapping
    @Operation(summary = "List all organizations")
    public ResponseEntity<List<Organization>> list() {
        return ResponseEntity.ok(adminOrgService.listOrganizations());
    }

    @PostMapping
    @Operation(summary = "Create organization and invite first Owner")
    public ResponseEntity<AdminOrgService.OrgCreationResult> create(
            @Valid @RequestBody CreateOrgRequest request) {
        AdminOrgService.OrgCreationResult result = adminOrgService.createOrganization(
            request.getOrgName(),
            request.getOwnerName(),
            request.getOwnerEmail()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization detail")
    public ResponseEntity<Organization> get(@PathVariable Long id) {
        return ResponseEntity.ok(adminOrgService.getOrganization(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Toggle organization active status")
    public ResponseEntity<Organization> patch(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        if (active == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(adminOrgService.toggleActive(id, active));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Permanently delete an organization and all its data (branches, users, masters)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        adminOrgService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }

    // --- Request DTO (inner class — no separate file needed, used only here) ---

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateOrgRequest {

        @NotBlank(message = "Organization name is required")
        private String orgName;

        @NotBlank(message = "Owner name is required")
        private String ownerName;

        @NotBlank(message = "Owner email is required")
        @Email(message = "Owner email must be a valid address")
        private String ownerEmail;
    }
}
