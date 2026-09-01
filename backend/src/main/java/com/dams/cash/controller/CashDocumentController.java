package com.dams.cash.controller;

import com.dams.cash.dto.CashDocumentPatchRequest;
import com.dams.cash.dto.CashDocumentResponse;
import com.dams.cash.dto.CreateCashDocumentRequest;
import com.dams.cash.service.CashDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Cash movements — the cashier's IN-from-bank / OUT-to-bank documents. Create / submit /
 * resubmit / edit while draft or queried / delete a draft. Verify + approve arrive with the
 * Accountant queue in Stage 7.
 */
@RestController
@RequestMapping("/api/v1/cash-documents")
@Tag(name = "Cash Documents", description = "Cashier bank <-> drawer cash movements")
@SecurityRequirement(name = "bearerAuth")
public class CashDocumentController {

    private final CashDocumentService cashDocumentService;

    public CashDocumentController(CashDocumentService cashDocumentService) {
        this.cashDocumentService = cashDocumentService;
    }

    @PostMapping
    @Operation(summary = "Record one IN or OUT cash movement")
    public ResponseEntity<CashDocumentResponse> create(@Valid @RequestBody CreateCashDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cashDocumentService.create(request));
    }

    @GetMapping
    @Operation(summary = "List a branch's cash movements for a day")
    public List<CashDocumentResponse> list(
        @RequestParam(name = "branchId", required = false) Long branchId,
        @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return cashDocumentService.listForDay(branchId, date);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a cash movement")
    public CashDocumentResponse get(@PathVariable Long id) {
        return cashDocumentService.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edit a cash movement (draft or queried only)")
    public CashDocumentResponse patch(@PathVariable Long id, @Valid @RequestBody CashDocumentPatchRequest request) {
        return cashDocumentService.patch(id, request);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a draft — assigns the gap-free C number")
    public CashDocumentResponse submit(@PathVariable Long id) {
        return cashDocumentService.submit(id);
    }

    @PostMapping("/{id}/resubmit")
    @Operation(summary = "Resubmit a queried movement after fixing it")
    public CashDocumentResponse resubmit(@PathVariable Long id) {
        return cashDocumentService.resubmit(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft cash movement")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        cashDocumentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
