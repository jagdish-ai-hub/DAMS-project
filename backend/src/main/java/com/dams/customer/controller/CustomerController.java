package com.dams.customer.controller;

import com.dams.customer.dto.CustomerExpenseEntry;
import com.dams.customer.dto.CustomerHistoryResponse;
import com.dams.customer.dto.CustomerRequest;
import com.dams.customer.dto.CustomerResponse;
import com.dams.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Customers. Any signed-in org user can read and create — the cashier flow creates a
 * customer inline while starting a job card. All actions are org-scoped by the service.
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Customer records and history")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @Operation(summary = "Search customers by name or phone (?q=)")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER')")
    public List<CustomerResponse> list(@RequestParam(name = "q", required = false) String q) {
        return customerService.search(q);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one customer with their vehicles")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER')")
    public CustomerResponse get(@PathVariable Long id) {
        return customerService.get(id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Customer history card — totals, job cards, payment timeline")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER')")
    public CustomerHistoryResponse history(@PathVariable Long id) {
        return customerService.history(id);
    }

    @GetMapping("/{id}/expenses")
    @Operation(summary = "Expenses tagged to this customer's job cards — fetched on demand")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER')")
    public List<CustomerExpenseEntry> expenses(@PathVariable Long id) {
        return customerService.expenses(id);
    }

    @PostMapping
    @Operation(summary = "Create a customer")
    @PreAuthorize("hasAnyAuthority('CASHIER','ACCOUNTANT','FINANCE_MANAGER')")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a customer's name / phone")
    @PreAuthorize("hasAnyAuthority('CASHIER','ACCOUNTANT','FINANCE_MANAGER')")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }
}
