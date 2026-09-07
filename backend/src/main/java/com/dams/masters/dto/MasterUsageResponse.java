package com.dams.masters.dto;

/** How often one master row was actually used in the last 90 days — the deactivation guard. */
public record MasterUsageResponse(
    Long id,
    String name,
    long useCount,
    boolean usedLast90d
) {
}
