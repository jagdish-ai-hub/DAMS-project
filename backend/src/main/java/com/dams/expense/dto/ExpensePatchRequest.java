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
 *
 * {@code customerName} / {@code vehicleNo} / {@code invoiceNo} / {@code dbmId} are manual
 * reference fields (see {@code ExpenseDocument}): null leaves them as-is, an empty string
 * clears them — same convention as {@code JobCardPatchRequest}.
 */
@Getter
@Setter
@NoArgsConstructor
public class ExpensePatchRequest {

    private Long jobCardId;

    /** Untag the expense from its job card (rev 56 -- the new picker offers "none"). */
    private Boolean clearJobCard;

    // Real links, same meaning as on CreateExpenseRequest (rev 56).
    private Long customerId;
    @Size(max = 160) private String newCustomerName;
    private Long vehicleId;
    @Size(max = 20) private String newVehicleNo;

    @Size(max = 160) private String customerName;
    @Size(max = 20) private String vehicleNo;
    @Size(max = 60) private String invoiceNo;
    @Size(max = 40) private String dbmId;

    private Long receiverId;
    @Size(max = 160) private String receiverName;
    @Size(max = 32) private String receiverPhone;

    private Long expenseCategoryId;
    private Long businessStatusId;
}
