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
 *   invoiceNo / invoiceAmount / dbmId  — editable at any time
 *   categoryId / businessStatusId      — editable only while the job card has no
 *                                        ClaimClose (Stage 8); a categoryId change writes
 *                                        a CATEGORY_CHANGED audit event
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

    private Long businessStatusId;
}
