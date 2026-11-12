package com.dams.spareparts.entity;

import com.dams.user.entity.Role;

/**
 * What an Inventory Manager or Store Keeper may do. The Owner edits the grid per role in
 * Masters; a missing row means the default below. Searching stock in every branch is NOT here:
 * it is fixed for both roles because it is the point of the module.
 */
public enum SparePermission {
    BILL_PART("Bill a part to a job card", true, true),
    RAISE_REQUEST("Raise a request to another branch", true, true),
    SEND_RECEIVE("Mark a request Sent / Received / Declined", true, true),
    UPLOAD_ADD("Upload an Excel file or add a part", false, true),
    CORRECT_QTY("Correct a quantity", false, true),
    EXPORT("Export stock to Excel", false, true);

    private final String label;
    private final boolean storeKeeperDefault;
    private final boolean inventoryManagerDefault;

    SparePermission(String label, boolean storeKeeperDefault, boolean inventoryManagerDefault) {
        this.label = label;
        this.storeKeeperDefault = storeKeeperDefault;
        this.inventoryManagerDefault = inventoryManagerDefault;
    }

    public String label() {
        return label;
    }

    /** The value used until the Owner changes it. Only the two Spare Parts roles have a grid. */
    public boolean defaultFor(Role role) {
        return switch (role) {
            case STORE_KEEPER -> storeKeeperDefault;
            case INVENTORY_MANAGER -> inventoryManagerDefault;
            default -> false;
        };
    }
}
