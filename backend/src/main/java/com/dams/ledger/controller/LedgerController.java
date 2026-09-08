package com.dams.ledger.controller;

import com.dams.ledger.dto.CustomerStatementResponse;
import com.dams.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Shareable customer statements (FEAT-43). Same visibility as customer
 * history — the statement shows nothing history doesn't.
 */
@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger statements", description = "FEAT-43: fleet-owner statements")
@SecurityRequirement(name = "bearerAuth")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/customers/{customerId}/statement")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER','AUDITOR')")
    @Operation(summary = "Customer statement — dues, payments and balance across all vehicles")
    public CustomerStatementResponse statement(@PathVariable Long customerId) {
        return ledgerService.statement(customerId);
    }
}
