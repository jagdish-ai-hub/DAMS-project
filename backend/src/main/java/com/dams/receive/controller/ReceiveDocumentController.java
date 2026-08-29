package com.dams.receive.controller;

import com.dams.attachment.dto.AttachmentResponse;
import com.dams.attachment.entity.ParentType;
import com.dams.attachment.service.AttachmentService;
import com.dams.receive.dto.CreateReceiptRequest;
import com.dams.receive.dto.ReceiveDocumentResponse;
import com.dams.receive.dto.SettlementLineInput;
import com.dams.receive.service.ReceiveDocumentService;
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
 * Receive documents — the cashier's receipt flow. Create (with inline job-card create) or
 * append to the job card's open document; submit / resubmit; Add Payment; edit or remove a
 * line while it is still a draft or queried; attach and list PDF/image receipts.
 *
 * Every write requires a CASHIER acting on a job card in their own home branch.
 */
@RestController
@RequestMapping("/api/v1/receipts")
@Tag(name = "Receive Documents", description = "Cashier receipts and settlement lines")
@SecurityRequirement(name = "bearerAuth")
public class ReceiveDocumentController {

    private final ReceiveDocumentService receiveDocumentService;
    private final AttachmentService attachmentService;

    public ReceiveDocumentController(ReceiveDocumentService receiveDocumentService,
                                    AttachmentService attachmentService) {
        this.receiveDocumentService = receiveDocumentService;
        this.attachmentService = attachmentService;
    }

    @PostMapping
    @Operation(summary = "Create a receipt (inline job-card create) or append to the open document")
    public ResponseEntity<ReceiveDocumentResponse> create(@Valid @RequestBody CreateReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(receiveDocumentService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a receive document with its lines and derived job-card fields")
    public ReceiveDocumentResponse get(@PathVariable Long id) {
        return receiveDocumentService.get(id);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a draft — assigns the gap-free document number and line ids")
    public ReceiveDocumentResponse submit(@PathVariable Long id) {
        return receiveDocumentService.submit(id);
    }

    @PostMapping("/{id}/resubmit")
    @Operation(summary = "Resubmit a queried document after fixing it")
    public ReceiveDocumentResponse resubmit(@PathVariable Long id) {
        return receiveDocumentService.resubmit(id);
    }

    @PostMapping("/{id}/lines")
    @Operation(summary = "Add Payment — append one settlement line to the open document")
    public ReceiveDocumentResponse addLine(@PathVariable Long id, @Valid @RequestBody SettlementLineInput input) {
        return receiveDocumentService.addLine(id, input);
    }

    @PatchMapping("/{id}/lines/{lineNo}")
    @Operation(summary = "Edit a settlement line (draft or queried documents only)")
    public ReceiveDocumentResponse updateLine(@PathVariable Long id, @PathVariable Integer lineNo,
                                              @Valid @RequestBody SettlementLineInput input) {
        return receiveDocumentService.updateLine(id, lineNo, input);
    }

    @DeleteMapping("/{id}/lines/{lineNo}")
    @Operation(summary = "Remove a settlement line (draft or queried documents only)")
    public ReceiveDocumentResponse deleteLine(@PathVariable Long id, @PathVariable Integer lineNo) {
        return receiveDocumentService.deleteLine(id, lineNo);
    }

    // --- attachments ---

    @PostMapping(value = "/{id}/attachments", consumes = "multipart/form-data")
    @Operation(summary = "Attach a PDF/image receipt to the whole document")
    public ResponseEntity<AttachmentResponse> attachToDocument(@PathVariable Long id,
                                                               @RequestParam("file") MultipartFile file) {
        AttachmentResponse saved = attachmentService.upload(ParentType.RECEIVE_DOCUMENT, id, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "List the document's attachments")
    public List<AttachmentResponse> documentAttachments(@PathVariable Long id) {
        return attachmentService.list(ParentType.RECEIVE_DOCUMENT, id);
    }

    @PostMapping(value = "/{id}/lines/{lineNo}/attachments", consumes = "multipart/form-data")
    @Operation(summary = "Attach a PDF/image receipt to one settlement line")
    public ResponseEntity<AttachmentResponse> attachToLine(@PathVariable Long id, @PathVariable Integer lineNo,
                                                           @RequestParam("file") MultipartFile file) {
        Long settlementLineId = receiveDocumentService.settlementLineId(id, lineNo);
        AttachmentResponse saved = attachmentService.upload(ParentType.SETTLEMENT_LINE, settlementLineId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{id}/lines/{lineNo}/attachments")
    @Operation(summary = "List a settlement line's attachments")
    public List<AttachmentResponse> lineAttachments(@PathVariable Long id, @PathVariable Integer lineNo) {
        Long settlementLineId = receiveDocumentService.settlementLineId(id, lineNo);
        return attachmentService.list(ParentType.SETTLEMENT_LINE, settlementLineId);
    }
}
