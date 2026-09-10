package com.dams.attachment.dto;

import jakarta.validation.constraints.Size;

/** {@code PATCH /attachments/{id}} body — a note about that one file. Blank clears it. */
public record AttachmentCommentRequest(
    @Size(max = 500) String comment
) {
}
