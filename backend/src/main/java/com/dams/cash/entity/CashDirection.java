package com.dams.cash.entity;

/** Which way the cash moved between the branch drawer and the bank. */
public enum CashDirection {
    /** Cash drawn from the bank into the drawer — adds to the drawer position. */
    IN,
    /** Cash taken from the drawer and deposited to the bank — subtracts from the drawer position. */
    OUT
}
