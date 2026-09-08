package com.dams.messaging.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Default sender (FEAT-36): logs the message and reports success, exactly
 * like LoggingEmailService. Swapping in WhatsApp Cloud API means adding one
 * {@code @Service} implementing {@link MessageSender} with higher precedence
 * and nothing else — callers only see this seam.
 */
@Service
public class LoggingMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessageSender.class);

    @Override
    public String providerName() {
        return "logging";
    }

    @Override
    public String send(String channel, String toPhone, String body, Map<String, String> variables) {
        // The invite-link equivalent: full content in the log so a test-phase
        // reviewer can see exactly what the customer would have received.
        log.info("MESSAGE [{}] to={} body={}", channel, toPhone, body);
        return null;
    }
}
