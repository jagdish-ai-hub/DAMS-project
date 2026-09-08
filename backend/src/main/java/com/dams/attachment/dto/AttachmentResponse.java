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
    Instant uploadedAt,
    // FEAT-37: exact duplicates found at upload (same SHA-256 elsewhere in the
    // org) — a warning, never a block. Null when none or not computed.
    java.util.List<DuplicateRef> duplicateOf
) {
    /** One other attachment holding byte-identical content. */
    public record DuplicateRef(Long attachmentId, String parentType, Long parentId, String filename) {
    }

    public static AttachmentResponse of(Attachment a) {
        return of(a, null);
    }

    public static AttachmentResponse of(Attachment a, java.util.List<DuplicateRef> duplicateOf) {
        return new AttachmentResponse(
            a.getId(), a.getFilename(), a.getContentType(), a.getSizeBytes(), a.isFrozen(), a.getUploadedAt(),
            duplicateOf == null || duplicateOf.isEmpty() ? null : duplicateOf);
    }
}
