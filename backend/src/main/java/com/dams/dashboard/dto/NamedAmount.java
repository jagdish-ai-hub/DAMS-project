package com.dams.dashboard.dto;

import java.math.BigDecimal;

/** One slice of a breakdown — a settlement mode, an expense category, etc. */
public record NamedAmount(String name, BigDecimal amount) {
}
