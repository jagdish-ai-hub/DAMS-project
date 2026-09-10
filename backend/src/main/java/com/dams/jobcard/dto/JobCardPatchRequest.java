package com.dams.jobcard.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Partial update of a job card. Only the fields you send are changed; a null field is left
 * as-is. To clear a text reference send an empty string.
 *
 *   invoiceNo / invoiceAmount / dbmId          — editable at any time
 *   categoryId / businessStatusId / claimTypeId — editable only while the job card has no
 *                                        ClaimClose (Stage 8); a categoryId change writes
 *                                        a CATEGORY_CHANGED audit event, a claimTypeId
 *                                        change a CLAIM_TYPE_CHANGED one. Send claimTypeId
 *                                        as 0 to clear it back to "not a claim" — 0 is not
 *                                        a valid id so it's unambiguous with "leave as-is".
 */
@Getter
@Setter
@NoArgsConstructor
public class JobCardPatchRequest {

    @Size(max = 60, message = "Invoice number must be at most 60 characters")
    private String invoiceNo;

    private BigDecimal invoiceAmount;

    @Size(max = 40, message = "DBM id must be at most 40 characters")
    private String dbmId;

    /** Send to switch B2C/B2B. When the effective value is B2B, a GST number must be on record. */
    private Boolean b2b;

    @Size(max = 20, message = "GST number must be at most 20 characters")
    private String gstNo;

    private Long categoryId;

    /** 0 clears it (job card is no longer a claim); null leaves it unchanged. */
    private Long claimTypeId;

    private Long businessStatusId;

    @Size(max = 20, message = "Vehicle number must be at most 20 characters")
    private String vehicleNo;

    /** If true, clears the invoice amount on the job card (sets it to null). */
    private Boolean clearInvoiceAmount;
}
