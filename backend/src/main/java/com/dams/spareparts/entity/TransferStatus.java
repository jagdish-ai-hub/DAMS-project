package com.dams.spareparts.entity;

/** Life of a branch request. A request never changes stock; Sent and Received do. */
public enum TransferStatus {
    REQUESTED,
    SENT,
    RECEIVED,
    DECLINED,
    CANCELLED
}
