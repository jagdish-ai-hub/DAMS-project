package com.dams.recon.dto;

import com.dams.recon.entity.ReconBatch;

import java.time.Instant;

public record ReconBatchResponse(
    Long id,
    String filename,
    int lineCount,
    int resolvedCount,
    Long uploadedBy,
    Instant uploadedAt
) {
    public static ReconBatchResponse of(ReconBatch b, int resolvedCount) {
        return new ReconBatchResponse(
            b.getId(), b.getFilename(), b.getLineCount(), resolvedCount,
            b.getUploadedBy(), b.getUploadedAt());
    }
}
