package com.dams.expense.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Header edits allowed while an expense document is still a DRAFT or QUERIED — the
 * fix-and-resubmit path. Every field is optional; only the non-null ones are applied.
 * Settlement of the individual lines is done through the line endpoints.
 *
 * A job card cannot be <i>removed</i> here (only changed to another in the same branch);
 * untagging an expense from its job card is not a Stage 5 flow.
 */
@Getter
@Setter
@NoArgsConstructor
public class ExpensePatchRequest {

    private Long jobCardId;

    private Long receiverId;
    @Size(max = 160) private String receiverName;
    @Size(max = 32) private String receiverPhone;

    private Long expenseCategoryId;
    private Long businessStatusId;
}
