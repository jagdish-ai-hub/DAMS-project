package com.dams.attachment.entity;

/**
 * What an {@link Attachment} hangs off. Polymorphic {@code (parent_type, parent_id)} — one
 * table serves the receive side (this stage) and the expense side (Stage 5).
 */
public enum ParentType {
    RECEIVE_DOCUMENT,
    SETTLEMENT_LINE,
    EXPENSE_DOCUMENT,
    EXPENSE_LINE
}
