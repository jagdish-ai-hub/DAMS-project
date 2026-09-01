package com.dams.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day on the collections-vs-expenses trend line. */
public record TrendPoint(LocalDate date, BigDecimal collections, BigDecimal expenses) {
}
