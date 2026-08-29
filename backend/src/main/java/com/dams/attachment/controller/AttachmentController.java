package com.dams.attachment.controller;

import com.dams.attachment.dto.SignedUrlResponse;
import com.dams.attachment.service.AttachmentService;
import com.dams.attachment.storage.LocalFilesystemStorageService;
import com.dams.attachment.storage.SignedUrlTokens;
import com.dams.common.exception.DamsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Cross-parent attachment endpoints. Uploads happen through the receipt/line endpoints
 * (see ReceiveDocumentController); here you exchange an attachment id for a short-lived
 * signed URL, delete an unfrozen attachment, or — with the local storage backend — stream
 * the bytes the signed URL points at.
 */
@RestController
@RequestMapping("/api/v1/attachments")
@Tag(name = "Attachments", description = "Signed-URL retrieval and deletion of receipts")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final SignedUrlTokens tokens;
    private final Optional<LocalFilesystemStorageService> localStorage;

    public AttachmentController(AttachmentService attachmentService,
                               SignedUrlTokens tokens,
                               Optional<LocalFilesystemStorageService> localStorage) {
        this.attachmentService = attachmentService;
        this.tokens = tokens;
        this.localStorage = localStorage;
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get a short-lived signed URL for an attachment")
    public SignedUrlResponse signedUrl(@PathVariable Long id) {
        return attachmentService.signedUrl(id);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete an attachment (409 if frozen)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Streams attachment bytes for the local storage backend. Unauthenticated on purpose —
     * the {@code sig} + {@code exp} query params ARE the authorisation (like an S3 presigned
     * URL). Never reached when {@code dams.storage.provider=r2} (R2 serves its own URLs).
     */
    @GetMapping("/raw")
    @Operation(summary = "Stream attachment bytes (local storage backend; signed URL only)")
    public ResponseEntity<byte[]> raw(@RequestParam String key,
                                      @RequestParam long exp,
                                      @RequestParam String sig) {
        LocalFilesystemStorageService storage = localStorage.orElseThrow(
            () -> DamsException.notFound("Raw attachment endpoint", "storage backend", "r2"));

        String objectKey;
        try {
            objectKey = tokens.decodeKey(key);
        } catch (IllegalArgumentException badKey) {
            throw DamsException.forbidden("Attachment link is invalid");
        }
        if (!tokens.isValid(objectKey, exp, sig)) {
            throw DamsException.forbidden("Attachment link has expired or been tampered with");
        }
        byte[] bytes = storage.read(objectKey);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(bytes);
    }
}
