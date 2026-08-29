package com.dams.auth.controller;

import com.dams.auth.dto.AcceptInviteRequest;
import com.dams.auth.dto.ChangePasswordRequest;
import com.dams.auth.dto.LoginRequest;
import com.dams.auth.dto.LoginResponse;
import com.dams.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Login, invite acceptance, and self-service password change. No refresh token in v1.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email + password — returns one access token (~8h)")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/accept-invite")
    @Operation(summary = "Accept an invite, set a password, and log in")
    public ResponseEntity<LoginResponse> acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        return ResponseEntity.ok(authService.acceptInvite(request));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change your own password (any signed-in user)")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }
}
