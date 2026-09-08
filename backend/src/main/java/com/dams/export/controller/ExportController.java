package com.dams.export.controller;

import com.dams.export.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/export")
@Tag(name = "Accounting Export", description = "Tally & Excel ledger CSV feeds for receipts and expenses")
@SecurityRequirement(name = "bearerAuth")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/receipts")
    @Operation(summary = "Export receipts ledger to Tally/Excel compatible CSV")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','AUDITOR')")
    public ResponseEntity<byte[]> exportReceipts(
        @RequestParam(name = "branchId", required = false) Long branchId,
        @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] csv = exportService.exportReceiptsCsv(branchId, from, to);
        String filename = "receipts-export-" + (from != null ? from : "all") + "-to-" + (to != null ? to : "today") + ".csv";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    @GetMapping("/expenses")
    @Operation(summary = "Export expenses ledger to Tally/Excel compatible CSV")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','AUDITOR')")
    public ResponseEntity<byte[]> exportExpenses(
        @RequestParam(name = "branchId", required = false) Long branchId,
        @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] csv = exportService.exportExpensesCsv(branchId, from, to);
        String filename = "expenses-export-" + (from != null ? from : "all") + "-to-" + (to != null ? to : "today") + ".csv";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }
}
