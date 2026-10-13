package com.dams.review.dto;

import jakarta.validation.constraints.Size;

/** rev 76 — the Finance Manager's optional comment on an approval ("approved because …"). */
public record ApproveRequest(@Size(max = 500, message = "The comment must be at most 500 characters") String comment) {
}
