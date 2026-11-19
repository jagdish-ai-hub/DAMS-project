package com.dams.invoiceextract.controller;

import com.dams.invoiceextract.dto.InvoiceExtractResponse;
import com.dams.invoiceextract.service.InvoiceExtractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Finance Manager's "Extract invoice" test tool (rev 98): read an Eicher dealer PDF, show the Spares / Lube / Labour detail. Saves nothing. */
@RestController
@RequestMapping("/api/v1/invoice-extract")
@Tag(name = "Invoice extract (test)", description = "Read an Eicher dealer invoice / warranty-claim PDF and return its tables")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceExtractController {

    private final InvoiceExtractService service;

    public InvoiceExtractController(InvoiceExtractService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FINANCE_MANAGER')")
    @Operation(summary = "Read a PDF and return the header, spares, lube, labour and the self-checks. Posts and stores nothing.")
    public InvoiceExtractResponse extract(@RequestParam("file") MultipartFile file) {
        return service.extract(file);
    }
}
