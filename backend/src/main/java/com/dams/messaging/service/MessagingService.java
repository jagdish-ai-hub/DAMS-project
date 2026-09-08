package com.dams.messaging.service;

import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.messaging.dto.MessageLogResponse;
import com.dams.messaging.dto.MessageTemplateResponse;
import com.dams.messaging.dto.SendMessageRequest;
import com.dams.messaging.entity.MessageLog;
import com.dams.messaging.entity.MessageTemplate;
import com.dams.messaging.repository.MessageLogRepository;
import com.dams.messaging.repository.MessageTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Templated outbound messaging (FEAT-36). Templates are org-owned copy with
 * {{placeholders}} — no free-typed blasts. Every attempt writes a
 * {@link MessageLog} row first (QUEUED), then SENT/LOGGED or FAILED, so
 * reminders are auditable. Also seeds the four default templates for orgs
 * created before V26 (V26 seeds existing rows at migrate time).
 */
@Service
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    private final MessageTemplateRepository templateRepo;
    private final MessageLogRepository logRepo;
    private final MessageSender sender;

    public MessagingService(MessageTemplateRepository templateRepo,
                            MessageLogRepository logRepo,
                            MessageSender sender) {
        this.templateRepo = templateRepo;
        this.logRepo = logRepo;
        this.sender = sender;
    }

    // Plain @Transactional (not read-only): first read seeds the defaults.
    @Transactional
    public List<MessageTemplateResponse> templates() {
        Long orgId = TenantContext.requireOrgId();
        ensureDefaults(orgId);
        return templateRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .map(MessageTemplateResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageLogResponse> recentLog() {
        return logRepo.findTop100ByOrgIdOrderByCreatedAtDesc(TenantContext.requireOrgId()).stream()
            .map(MessageLogResponse::of).toList();
    }

    @Transactional
    public MessageLogResponse send(SendMessageRequest request) {
        Long orgId = TenantContext.requireOrgId();
        return sendAs(orgId, null, request.getTemplateCode(), request.getToPhone(),
            request.getVariables(), request.getRelatedType(), request.getRelatedId());
    }

    /**
     * Render + deliver one message inside the caller's org. Used by flows
     * (reminders, renewals, digest) as well as the manual send endpoint.
     */
    @Transactional
    public MessageLogResponse sendAs(Long orgId, Long actorUserId, String templateCode,
                                     String toPhone, Map<String, String> variables,
                                     String relatedType, Long relatedId) {
        if (toPhone == null || toPhone.isBlank()) {
            throw DamsException.badRequest("A recipient phone number is required — nobody gets nagged by accident");
        }
        ensureDefaults(orgId);
        MessageTemplate template = templateRepo.findByOrgIdAndCode(orgId, templateCode)
            .orElseThrow(() -> DamsException.notFound("Message template", templateCode));
        if (!template.isActive()) {
            throw DamsException.conflict("Template '" + templateCode + "' is deactivated");
        }
        String body = render(template.getBody(), variables);

        MessageLog row = new MessageLog();
        row.setOrgId(orgId);
        row.setChannel(template.getChannel());
        row.setToPhone(toPhone.trim());
        row.setTemplateCode(templateCode);
        row.setBody(body);
        row.setRelatedType(relatedType);
        row.setRelatedId(relatedId);
        row.setCreatedBy(actorUserId);
        row = logRepo.save(row);

        boolean realProvider = !"logging".equals(sender.providerName());
        try {
            sender.send(template.getChannel(), row.getToPhone(), body, variables == null ? Map.of() : variables);
            row.setStatus(realProvider ? "SENT" : "LOGGED");
        } catch (RuntimeException e) {
            row.setStatus("FAILED");
            row.setError(e.getMessage());
            log.warn("Message failed: orgId={} logId={} to={} error={}", orgId, row.getId(), row.getToPhone(), e.getMessage());
        }
        row = logRepo.save(row);
        log.info("Message {}: orgId={} logId={} template={} to={} provider={}",
            row.getStatus(), orgId, row.getId(), templateCode, row.getToPhone(), sender.providerName());
        return MessageLogResponse.of(row);
    }

    /** {{name}} replacement. Missing variables render empty, never crash. */
    public static String render(String body, Map<String, String> variables) {
        if (body == null) {
            return "";
        }
        Map<String, String> vars = variables == null ? Map.of() : variables;
        Matcher m = PLACEHOLDER.matcher(body);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = vars.getOrDefault(m.group(1), "");
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private void ensureDefaults(Long orgId) {
        if (!templateRepo.findByOrgIdOrderByCodeAsc(orgId).isEmpty()) {
            return;
        }
        Map<String, String> defaults = new HashMap<>();
        defaults.put("payment_received",
            "DAMS: received Rs.{{amount}} from {{name}} ({{docNo}}). Balance due: Rs.{{balance}}. Thank you!");
        defaults.put("due_reminder",
            "DAMS:Dear {{name}}, Rs.{{amount}} ({{docNo}}) was due on {{dueDate}}. Please pay at the earliest. {{branch}}");
        defaults.put("renewal_reminder",
            "DAMS:Dear {{name}}, your {{vehicleNo}} AMC/service is due around {{dueDate}}. Book your slot: {{branch}}.");
        defaults.put("owner_digest",
            "DAMS daybook {{date}}: collected Rs.{{collections}}, spent Rs.{{expenses}}, net Rs.{{net}}. {{pending}} awaiting review, {{unclosed}} branches unclosed.");
        for (Map.Entry<String, String> e : defaults.entrySet()) {
            MessageTemplate t = new MessageTemplate();
            t.setOrgId(orgId);
            t.setCode(e.getKey());
            t.setChannel("WHATSAPP");
            t.setBody(e.getValue());
            templateRepo.save(t);
        }
        log.info("Seeded default message templates: orgId={}", orgId);
    }
}
