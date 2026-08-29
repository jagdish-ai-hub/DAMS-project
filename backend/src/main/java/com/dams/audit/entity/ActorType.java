package com.dams.audit.entity;

/** Who caused an {@link AuditEvent}. SYSTEM changes carry a null actor id. */
public enum ActorType {
    USER,
    SYSTEM
}
