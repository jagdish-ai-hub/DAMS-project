package com.dams.expense.controller;

import com.dams.attachment.dto.AttachmentResponse;
import com.dams.attachment.entity.ParentType;
import com.dams.attachment.service.AttachmentService;
import com.dams.expense.dto.CreateExpenseRequest;
import com.dams.expense.dto.ExpenseDocumentResponse;
import com.dams.expense.dto.ExpenseLineInput;
import com.dams.expense.dto.ExpensePatchRequest;
import com.dams.expense.service.ExpenseDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Expense documents — the cashier's expense flow. Create (with inline receiver create,
 * optional job-card tag); submit / resubmit; edit the header or a line while it is a draft
 * or queried; Add Expense onto an open document; move an expense onto a claim; attach and
 * list PDF/image receipts.
 *
 * Every write requires a CASHIER posting under their own home branch.
 */
@RestController
@RequestMapping("/api/v1/expenses")
@Tag(name = "Expense Documents", description = "Cashier expenses and expense lines")
@SecurityRequirement(name = "bearerAuth")
public class ExpenseDocumentController {

    private final ExpenseDocumentService expenseDocumentService;
    private final AttachmentService attachmentService;

    public ExpenseDocumentController(ExpenseDocumentService expenseDocumentService,
                                    AttachmentService attachmentService) {
        this.expenseDocumentService = expenseDocumentService;
        this.attachmentService = attachmentService;
    }

    @PostMapping
    @Operation(summary = "Create an expense (inline receiver create, optional job-card tag)")
    public ResponseEntity<ExpenseDocumentResponse> create(@Valid @RequestBody CreateExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseDocumentService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an expense document with its lines and derived fields")
    public ExpenseDocumentResponse get(@PathVariable Long id) {
        return expenseDocumentService.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edit the expense header (draft or queried documents only)")
    public ExpenseDocumentResponse patch(@PathVariable Long id, @Valid @RequestBody ExpensePatchRequest request) {
        return expenseDocumentService.patch(id, request);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a draft — assigns the gap-free document number and line ids")
    public ExpenseDocumentResponse submit(@PathVariable Long id) {
        return expenseDocumentService.submit(id);
    }

    @PostMapping("/{id}/resubmit")
    @Operation(summary = "Resubmit a queried document after fixing it")
    public ExpenseDocumentResponse resubmit(@PathVariable Long id) {
        return expenseDocumentService.resubmit(id);
    }

    @PostMapping("/{id}/transfer-to-claim")
    @Operation(summary = "Move the expense onto a warranty / AMC / goodwill claim")
    public ExpenseDocumentResponse transferToClaim(@PathVariable Long id) {
        return expenseDocumentService.transferToClaim(id);
    }

    @PostMapping("/{id}/lines")
    @Operation(summary = "Add Expense — append one line to an open document")
    public ExpenseDocumentResponse addLine(@PathVariable Long id, @Valid @RequestBody ExpenseLineInput input) {
        return expenseDocumentService.addLine(id, input);
    }

    @PatchMapping("/{id}/lines/{lineNo}")
    @Operation(summary = "Edit an expense line (draft or queried documents only)")
    public ExpenseDocumentResponse updateLine(@PathVariable Long id, @PathVariable Integer lineNo,
                                              @Valid @RequestBody ExpenseLineInput input) {
        return expenseDocumentService.updateLine(id, lineNo, input);
    }

    @DeleteMapping("/{id}/lines/{lineNo}")
    @Operation(summary = "Remove an expense line (draft or queried documents only)")
    public ExpenseDocumentResponse deleteLine(@PathVariable Long id, @PathVariable Integer lineNo) {
        return expenseDocumentService.deleteLine(id, lineNo);
    }

    // --- attachments ---

    @PostMapping(value = "/{id}/attachments", consumes = "multipart/form-data")
    @Operation(summary = "Attach a PDF/image receipt to the whole document")
    public ResponseEntity<AttachmentResponse> attachToDocument(@PathVariable Long id,
                                                               @RequestParam("file") MultipartFile file) {
        AttachmentResponse saved = attachmentService.upload(ParentType.EXPENSE_DOCUMENT, id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "List the document's attachments")
    public List<AttachmentResponse> documentAttachments(@PathVariable Long id) {
        return attachmentService.list(ParentType.EXPENSE_DOCUMENT, id);
    }

    @PostMapping(value = "/{id}/lines/{lineNo}/attachments", consumes = "multipart/form-data")
    @Operation(summary = "Attach a PDF/image receipt to one expense line")
    public ResponseEntity<AttachmentResponse> attachToLine(@PathVariable Long id, @PathVariable Integer lineNo,
                                                           @RequestParam("file") MultipartFile file) {
        Long expenseLineId = expenseDocumentService.expenseLineId(id, lineNo);
        AttachmentResponse saved = attachmentService.upload(ParentType.EXPENSE_LINE, expenseLineId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}/lines/{lineNo}/attachments")
    @Operation(summary = "List an expense line's attachments")
    public List<AttachmentResponse> lineAttachments(@PathVariable Long id, @PathVariable Integer lineNo) {
        Long expenseLineId = expenseDocumentService.expenseLineId(id, lineNo);
        return attachmentService.list(ParentType.EXPENSE_LINE, expenseLineId);
    }
}
