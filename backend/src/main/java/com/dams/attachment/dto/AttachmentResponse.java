package com.dams.attachment.dto;

import com.dams.attachment.entity.Attachment;

import java.time.Instant;

/** Metadata for one attachment. The bytes are fetched separately via a signed URL. */
public record AttachmentResponse(
    Long id,
    String filename,
    String contentType,
    long sizeBytes,
    boolean frozen,
    Instant uploadedAt
) {
    public static AttachmentResponse of(Attachment a) {
        return new AttachmentResponse(
            a.getId(), a.getFilename(), a.getContentType(), a.getSizeBytes(), a.isFrozen(), a.getUploadedAt());
    }
}
