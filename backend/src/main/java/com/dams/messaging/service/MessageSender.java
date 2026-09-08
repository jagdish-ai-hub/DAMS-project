package com.dams.messaging.service;

import java.util.Map;

/**
 * Outbound message delivery seam (FEAT-36), mirroring EmailService and
 * StorageService: one small provider-agnostic interface, so WhatsApp today
 * and SMS tomorrow plug in without touching business logic. v1 ships only
 * {@link LoggingMessageSender} — messages are logged + recorded, never lost,
 * and visibly marked until a real provider is configured.
 */
public interface MessageSender {

    /** Provider name for the log, e.g. "logging" or "whatsapp-cloud". */
    String providerName();

    /**
     * Deliver one rendered message.
     *
     * @return the provider's message id, or null when there is none
     * @throws RuntimeException when delivery fails (the caller logs FAILED)
     */
    String send(String channel, String toPhone, String body, Map<String, String> variables);
}
