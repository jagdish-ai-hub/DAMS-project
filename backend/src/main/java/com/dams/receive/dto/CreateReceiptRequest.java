package com.dams.receive.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The single continuous receipt flow (AGENT.md "Job card creation"). Either:
 *   - point at an existing job card with {@code jobCardId}; or
 *   - create one inline from the header fields below (customer by id or name, vehicle by id
 *     or number, category + business status, invoice / dbm / B2B).
 *
 * There is deliberately <b>no branch field</b> — the document always posts under the job
 * card's branch, which for a cashier is their fixed home branch.
 *
 * {@code lines} may be empty (Save Draft with the header only). {@code submit = true} also
 * submits the document (assigns its number, stamps line ids, moves it to SUBMITTED).
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateReceiptRequest {

    private Long jobCardId;

    // --- inline job card (used only when jobCardId is null) ---
    private Long customerId;
    @Size(max = 160) private String customerName;
    @Size(max = 32) private String customerPhone;
    private Long vehicleId;
    @Size(max = 20) private String vehicleNo;
    @Size(max = 40) private String dbmId;
    @Size(max = 60) private String invoiceNo;
    private BigDecimal invoiceAmount;
    private Boolean b2b;
    @Size(max = 20) private String gstNo;
    private Long categoryId;
    private Long businessStatusId;

    // --- settlement lines ---
    @Valid
    private List<SettlementLineInput> lines = new ArrayList<>();

    private boolean submit;

    public boolean hasJobCardId() {
        return jobCardId != null;
    }
}
