package com.dams.staff.controller;

import com.dams.staff.dto.CreateAdvanceEntryRequest;
import com.dams.staff.dto.CreateStaffRequest;
import com.dams.staff.dto.StaffAdvanceEntryResponse;
import com.dams.staff.dto.StaffMemberResponse;
import com.dams.staff.service.StaffService;
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
 * Staff advance ledger (FEAT-44). Advances out, recoveries in, outstanding
 * derived. Entries are never edited or deleted — a wrong entry is fixed by
 * an opposing entry.
 */
@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff advances", description = "FEAT-44: advance/recovery ledger per staff")
@SecurityRequirement(name = "bearerAuth")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER','AUDITOR')")
    @Operation(summary = "Staff with outstanding advance balances")
    public List<StaffMemberResponse> members() {
        return staffService.members();
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER')")
    @Operation(summary = "Add (or reactivate) a staff member")
    public ResponseEntity<StaffMemberResponse> addMember(@Valid @RequestBody CreateStaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.addMember(request));
    }

    @PostMapping("/{staffId}/deactivate")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT')")
    @Operation(summary = "Deactivate a staff member (history is kept)")
    public StaffMemberResponse deactivate(@PathVariable Long staffId) {
        return staffService.deactivate(staffId);
    }

    @GetMapping("/{staffId}/entries")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER','AUDITOR')")
    @Operation(summary = "Advance/recovery entries for one staff member")
    public List<StaffAdvanceEntryResponse> entries(@PathVariable Long staffId) {
        return staffService.entries(staffId);
    }

    @PostMapping("/{staffId}/entries")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER')")
    @Operation(summary = "Record an ADVANCE out or a RECOVERY in")
    public ResponseEntity<StaffAdvanceEntryResponse> recordEntry(
            @PathVariable Long staffId, @Valid @RequestBody CreateAdvanceEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.recordEntry(staffId, request));
    }
}
