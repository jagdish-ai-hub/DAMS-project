package com.dams.recon.controller;

import com.dams.recon.dto.ReconBatchResponse;
import com.dams.recon.dto.ReconLineResponse;
import com.dams.recon.service.ReconService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Bank statement reconciliation (FEAT-40). Upload a statement CSV, confirm or
 * ignore each suggested match. Matching only explains money — it never moves
 * it or edits a document. Accountant/FM/Owner: the people who reconcile.
 */
@RestController
@RequestMapping("/api/v1/recon")
@Tag(name = "Reconciliation", description = "FEAT-40: match bank credits to receipts")
@SecurityRequirement(name = "bearerAuth")
public class ReconController {

    private final ReconService reconService;

    public ReconController(ReconService reconService) {
        this.reconService = reconService;
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','AUDITOR')")
    @Operation(summary = "Statement upload batches, newest first")
    public List<ReconBatchResponse> batches() {
        return reconService.batches();
    }

    @GetMapping("/batches/{batchId}/lines")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','AUDITOR')")
    @Operation(summary = "Statement lines with match suggestions for one batch")
    public List<ReconLineResponse> lines(@PathVariable Long batchId) {
        return reconService.lines(batchId);
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT')")
    @Operation(summary = "Upload a statement CSV (header date,utr,amount[,narration]) and auto-suggest matches")
    public ResponseEntity<ReconBatchResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reconService.upload(file));
    }

    @PostMapping("/lines/{lineId}/confirm")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT')")
    @Operation(summary = "Confirm a suggested match (or record a manual pick)")
    public ReconLineResponse confirm(@PathVariable Long lineId,
                                     @RequestParam("settlementLineId") Long settlementLineId) {
        return reconService.confirm(lineId, settlementLineId);
    }

    @PostMapping("/lines/{lineId}/ignore")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT')")
    @Operation(summary = "Ignore a line (bank charge, transfer, already explained)")
    public ReconLineResponse ignore(@PathVariable Long lineId,
                                    @RequestParam(name = "ignored", defaultValue = "true") boolean ignored) {
        return reconService.ignore(lineId, ignored);
    }
}
