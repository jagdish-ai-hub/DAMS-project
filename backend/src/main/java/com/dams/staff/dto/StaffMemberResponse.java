package com.dams.staff.dto;

import java.math.BigDecimal;

public record StaffMemberResponse(
    Long id,
    String name,
    String phone,
    boolean active,
    BigDecimal outstanding
) {
}
