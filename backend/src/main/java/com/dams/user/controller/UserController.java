package com.dams.user.controller;

import com.dams.user.dto.UserRequest;
import com.dams.user.dto.UserResponse;
import com.dams.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Team management — Owner only. Creating a user sends an invite (logged in v1); the
 * response includes the invite link to pass on. Users are deactivated, never deleted.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Owner: manage team members and branch access")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('OWNER')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List team members")
    public List<UserResponse> list() {
        return userService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one team member")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @Operation(summary = "Add a team member (issues an invite)")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a team member (name / role / branch access / active)")
    public UserResponse update(@PathVariable Long id,
                               @Valid @RequestBody UserRequest request,
                               Authentication authentication) {
        Long callerUserId = (Long) authentication.getPrincipal();
        return userService.update(id, request, callerUserId);
    }
}
