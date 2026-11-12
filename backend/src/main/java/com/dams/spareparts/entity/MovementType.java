package com.dams.spareparts.entity;

/** What a stock-ledger row records. Mirrors the CHECK constraint on inv_movement.movement_type. */
public enum MovementType {
    /** An Excel upload in "Add to stock" mode. */
    UPLOAD_ADD,
    /** An Excel upload in "Set stock to the file" mode (the row holds the difference). */
    UPLOAD_SET,
    /** One part added by hand with an opening quantity. */
    MANUAL_ADD,
    /** A quantity corrected by an Inventory Manager, with a reason. */
    CORRECTION,
    /** A part billed against a job card. */
    BILLED,
    /** Marked Sent by the supplying branch: the part is now in transit. */
    TRANSFER_OUT,
    /** Marked Received by the requesting branch. */
    TRANSFER_IN
}
