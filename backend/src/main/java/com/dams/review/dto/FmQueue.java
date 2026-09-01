package com.dams.review.dto;

import java.util.List;

/**
 * The Finance Manager's queue for one document type. {@code awaitingApproval} is every
 * VERIFIED document (org-wide). {@code openClaims} and {@code recentlyClosed} are the
 * claim lifecycle and are populated for receipts only — expenses have no claim step, so
 * both are empty there.
 */
public record FmQueue(
    List<ReviewQueueItem> awaitingApproval,
    List<ReviewQueueItem> openClaims,
    List<ReviewQueueItem> recentlyClosed
) {
}
