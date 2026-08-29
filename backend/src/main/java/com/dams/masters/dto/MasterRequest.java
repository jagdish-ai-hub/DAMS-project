package com.dams.masters.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One request shape for all eight masters. Only the fields relevant to the target type
 * are read; the rest are ignored. See MasterType.
 */
@Getter
@Setter
@NoArgsConstructor
public class MasterRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be at most 120 characters")
    private String name;

    /** Optional on update; ignored on create (new rows are active). */
    private Boolean active;

    @PositiveOrZero(message = "sortOrder must be zero or positive")
    private Integer sortOrder;

    // receive-categories only
    private Boolean isClaim;

    // settlement-modes only
    private Boolean requiresBank;
    private Boolean requiresRef;

    // expense-sub-categories only
    private Long expenseCategoryId;
    private BigDecimal limitAmount;
}
