package com.dams.jobcard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Create a job card. The customer and vehicle can be given by id (existing) or created
 * inline:
 *   customer — {@code customerId}, OR {@code customerName} (+ optional {@code customerPhone})
 *   vehicle  — {@code vehicleId}, OR {@code vehicleNo} (deduped per org), OR neither
 *
 * {@code branchId} is required for OWNER / FINANCE_MANAGER / ACCOUNTANT and must be within
 * their branch scope; for a CASHIER it is ignored and forced to their home branch.
 */
@Getter
@Setter
@NoArgsConstructor
public class JobCardCreateRequest {

    private Long customerId;

    @Size(max = 160, message = "Customer name must be at most 160 characters")
    private String customerName;

    @Size(max = 32, message = "Phone must be at most 32 characters")
    private String customerPhone;

    private Long vehicleId;

    @Size(max = 20, message = "Vehicle number must be at most 20 characters")
    private String vehicleNo;

    private Long branchId;

    @NotNull(message = "categoryId is required")
    private Long categoryId;

    @NotNull(message = "businessStatusId is required")
    private Long businessStatusId;

    @Size(max = 40, message = "DBM id must be at most 40 characters")
    private String dbmId;

    @Size(max = 60, message = "Invoice number must be at most 60 characters")
    private String invoiceNo;

    private BigDecimal invoiceAmount;

    /** B2C when null/false. When true, {@code gstNo} is required. */
    private Boolean b2b;

    @Size(max = 20, message = "GST number must be at most 20 characters")
    private String gstNo;
}
