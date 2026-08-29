package com.dams.receiver.controller;

import com.dams.receiver.dto.ReceiverRequest;
import com.dams.receiver.dto.ReceiverResponse;
import com.dams.receiver.service.ReceiverService;
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
 * Receiver (vendor/payee) master. Reads open to any authenticated org user (the expense
 * form needs them); writes OWNER-only.
 */
@RestController
@RequestMapping("/api/v1/receivers")
@Tag(name = "Receivers", description = "Owner: manage vendors / payees")
@SecurityRequirement(name = "bearerAuth")
public class ReceiverController {

    private final ReceiverService receiverService;

    public ReceiverController(ReceiverService receiverService) {
        this.receiverService = receiverService;
    }

    @GetMapping
    @Operation(summary = "List receivers")
    public List<ReceiverResponse> list() {
        return receiverService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one receiver")
    public ReceiverResponse get(@PathVariable Long id) {
        return receiverService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(summary = "Create a receiver")
    public ResponseEntity<ReceiverResponse> create(@Valid @RequestBody ReceiverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(receiverService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('OWNER')")
    @Operation(summary = "Update a receiver")
    public ReceiverResponse update(@PathVariable Long id, @Valid @RequestBody ReceiverRequest request) {
        return receiverService.update(id, request);
    }
}
