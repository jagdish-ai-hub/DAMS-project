package com.dams.audit.controller;

import com.dams.audit.dto.OverrideAuditEntry;
import com.dams.audit.service.OverrideAuditService;
import com.dams.common.time.OrgTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Org-wide Override Audit — every amount override across the organisation (Accountant line
 * overrides + Finance Manager claim-close overrides), newest first. Owner and Finance
 * Manager only.
 *
 * {@code from} / {@code to} are inclusive calendar dates in the org timezone; both default
 * to the last 90 days.
 */
@RestController
@RequestMapping("/api/v1/override-audit")
@Tag(name = "Override Audit", description = "Every amount override across the organisation")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
public class OverrideAuditController {

    private final OverrideAuditService overrideAuditService;

    public OverrideAuditController(OverrideAuditService overrideAuditService) {
        this.overrideAuditService = overrideAuditService;
    }

    @GetMapping
    @Operation(summary = "List amount overrides, filtered by user / branch / date range")
    public List<OverrideAuditEntry> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate toDate = to != null ? to : OrgTime.today();
        LocalDate fromDate = from != null ? from : toDate.minusDays(90);
        Instant fromInstant = fromDate.atStartOfDay(OrgTime.ZONE).toInstant();
        Instant toInstant = toDate.plusDays(1).atStartOfDay(OrgTime.ZONE).toInstant(); // exclusive end

        return overrideAuditService.list(fromInstant, toInstant, branchId, userId);
    }
}
