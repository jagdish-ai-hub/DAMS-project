package com.dams.spareparts.entity;

/** What an Excel upload does to the quantities it names. The user is asked every time. */
public enum ImportMode {
    /** Add the file's quantity to what the branch already holds. */
    ADD,
    /** Set the quantity of the parts in the file; parts not in the file are never touched. */
    SET
}
