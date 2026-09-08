package com.dams.customer.dto;

import java.math.BigDecimal;

/** B2B exposure vs limit (FEAT-46): warn-first, never a posting block in v1. */
public record CreditStatusResponse(
    Long customerId,
    String customerName,
    BigDecimal creditLimit,
    BigDecimal exposure,
    boolean breached,
    BigDecimal headroom
) {
}
