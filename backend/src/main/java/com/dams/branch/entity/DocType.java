package com.dams.branch.entity;

/**
 * Document series type for the gap-free per-branch/per-month counter.
 * R = Receive, E = Expense, C = Cash. Stored as the single-character name.
 */
public enum DocType {
    R,
    E,
    C
}
