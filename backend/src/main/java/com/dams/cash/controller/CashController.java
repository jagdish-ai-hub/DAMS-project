package com.dams.cash.controller;

import com.dams.cash.dto.CashDayCloseResponse;
import com.dams.cash.dto.CashDrawerResponse;
import com.dams.cash.dto.CashOpeningRequest;
import com.dams.cash.dto.CloseDayRequest;
import com.dams.cash.service.CashCloseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * The Cash page's day operations: the live drawer position, the Accountant's one-time branch
 * opening, and the cashier's end-of-day close.
 */
@RestController
@RequestMapping("/api/v1/cash")
@Tag(name = "Cash Page", description = "Drawer position, branch opening, day close")
@SecurityRequirement(name = "bearerAuth")
public class CashController {

    private final CashCloseService cashCloseService;

    public CashController(CashCloseService cashCloseService) {
        this.cashCloseService = cashCloseService;
    }

    @GetMapping("/drawer")
    @Operation(summary = "Live drawer position + breakdown + today's movements for a branch/date")
    public CashDrawerResponse drawer(
        @RequestParam(name = "branchId", required = false) Long branchId,
        @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return cashCloseService.drawer(branchId, date);
    }

    @PostMapping("/opening")
    @Operation(summary = "ACCOUNTANT — set a branch's first-ever opening cash balance")
    public ResponseEntity<CashDrawerResponse> setOpening(@Valid @RequestBody CashOpeningRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cashCloseService.setOpening(request));
    }

    @PostMapping("/close-day")
    @Operation(summary = "CASHIER — close the day: counted amount + variance remark; locks the date")
    public ResponseEntity<CashDayCloseResponse> closeDay(@Valid @RequestBody CloseDayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cashCloseService.closeDay(request));
    }

    @GetMapping("/close-day")
    @Operation(summary = "Get the recorded close for a branch/date (404 if the day is still open)")
    public CashDayCloseResponse getClose(
        @RequestParam(name = "branchId", required = false) Long branchId,
        @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return cashCloseService.getClose(branchId, date);
    }
}
