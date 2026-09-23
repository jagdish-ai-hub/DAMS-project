package com.dams.appearance.controller;

import com.dams.appearance.service.AppearanceService;
import com.dams.common.security.BranchScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Platform appearance (UI font). The read is public — the login screen renders before
 * anyone signs in; the write is Super Admin only. See AGENT.md "Appearance — platform font".
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Appearance", description = "Platform UI font (Super Admin)")
public class AppearanceController {

    private final AppearanceService appearanceService;
    private final BranchScope branchScope;

    public AppearanceController(AppearanceService appearanceService, BranchScope branchScope) {
        this.appearanceService = appearanceService;
        this.branchScope = branchScope;
    }

    public record AppearanceResponse(String font, List<String> available) {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AppearanceRequest {
        @NotBlank
        private String font;
    }

    @GetMapping("/public/appearance")
    @Operation(summary = "Current platform UI font (unauthenticated)")
    public ResponseEntity<AppearanceResponse> get() {
        return ResponseEntity.ok(new AppearanceResponse(appearanceService.currentFont(), AppearanceService.ALLOWED_FONTS));
    }

    @PutMapping("/admin/appearance")
    @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Set the platform UI font (Super Admin)")
    public ResponseEntity<AppearanceResponse> put(@Valid @RequestBody AppearanceRequest request) {
        String font = appearanceService.setFont(request.getFont(), branchScope.currentUserId());
        return ResponseEntity.ok(new AppearanceResponse(font, AppearanceService.ALLOWED_FONTS));
    }
}
