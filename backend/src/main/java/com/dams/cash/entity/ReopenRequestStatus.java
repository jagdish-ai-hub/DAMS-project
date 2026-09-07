package com.dams.cash.entity;

/** Lifecycle of a cash-close reopen request — decided rows stay as history. */
public enum ReopenRequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
