package com.dams.messaging.controller;

import com.dams.messaging.dto.MessageLogResponse;
import com.dams.messaging.dto.MessageTemplateResponse;
import com.dams.messaging.dto.SendMessageRequest;
import com.dams.messaging.service.MessagingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Templated messaging (FEAT-36). Templates are Owner-managed copy; sending is
 * open to transacting roles (counter confirmations, manual reminders); the
 * log is visible to Owner/FM/Accountant for audit.
 */
@RestController
@RequestMapping("/api/v1/messages")
@Tag(name = "Messaging", description = "FEAT-36: templated WhatsApp/SMS + send log")
@SecurityRequirement(name = "bearerAuth")
public class MessageController {

    private final MessagingService messagingService;

    public MessageController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER','AUDITOR')")
    @Operation(summary = "List the org's message templates (defaults seeded on first read)")
    public List<MessageTemplateResponse> templates() {
        return messagingService.templates();
    }

    @GetMapping("/log")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','AUDITOR')")
    @Operation(summary = "Recent send attempts, newest first")
    public List<MessageLogResponse> recentLog() {
        return messagingService.recentLog();
    }

    @PostMapping("/send")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT','CASHIER')")
    @Operation(summary = "Render a template and send it now")
    public MessageLogResponse send(@Valid @RequestBody SendMessageRequest request) {
        return messagingService.send(request);
    }
}
