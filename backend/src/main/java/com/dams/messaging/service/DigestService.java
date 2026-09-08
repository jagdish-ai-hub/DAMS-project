package com.dams.messaging.service;

import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.dashboard.service.DashboardService;
import com.dams.messaging.dto.MessageLogResponse;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nightly owner daybook (FEAT-42): collections/expenses/net, awaiting review,
 * unclosed branches — pushed at 20:00 IST where the owner already is
 * (WhatsApp), instead of waiting in a dashboard they rarely open. Read-only
 * push: aggregates already computed for the dashboard, zero new money logic.
 * Explicit org opt-in only ({@code digest_enabled}); owners without a phone
 * on file are skipped, never guessed.
 */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private final OrganizationRepository orgRepo;
    private final AppUserRepository userRepo;
    private final DashboardService dashboardService;
    private final MessagingService messagingService;

    public DigestService(OrganizationRepository orgRepo,
                         AppUserRepository userRepo,
                         DashboardService dashboardService,
                         MessagingService messagingService) {
        this.orgRepo = orgRepo;
        this.userRepo = userRepo;
        this.dashboardService = dashboardService;
        this.messagingService = messagingService;
    }

    /** Every night at 20:00 India time. One org's failure never stops the rest. */
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Kolkata")
    public void sendNightlyDigests() {
        for (Organization org : orgRepo.findByDigestEnabledTrueAndActiveTrue()) {
            try {
                sendForOrg(org.getId());
            } catch (RuntimeException e) {
                log.warn("Owner digest failed: orgId={} error={}", org.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }

    void sendForOrg(Long orgId) {
        TenantContext.setOrgId(orgId);
        List<AppUser> owners = userRepo.findByOrganization_Id(orgId).stream()
            .filter(u -> u.getRole() == Role.OWNER && u.isActive()
                && u.getPhone() != null && !u.getPhone().isBlank())
            .toList();
        if (owners.isEmpty()) {
            log.info("Owner digest skipped: orgId={} (no owner phone on file)", orgId);
            return;
        }
        var summary = dashboardService.summary(null, "today");
        LocalDate today = OrgTime.today();
        long unclosed = summary.branchComparison().stream()
            .filter(r -> r.lastClosed() == null || r.lastClosed().isBefore(today))
            .count();
        Map<String, String> vars = new HashMap<>();
        vars.put("date", today.toString());
        vars.put("collections", money(summary.kpis().collections()));
        vars.put("expenses", money(summary.kpis().expenses()));
        vars.put("net", money(summary.kpis().net()));
        vars.put("pending", String.valueOf(summary.kpis().pendingReview()));
        vars.put("unclosed", String.valueOf(unclosed));
        for (AppUser owner : owners) {
            MessageLogResponse sent = messagingService.sendAs(
                orgId, null, "owner_digest", owner.getPhone(), vars, "Digest", null);
            log.info("Owner digest {}: orgId={} ownerId={}", sent.status(), orgId, owner.getId());
        }
    }

    private static String money(java.math.BigDecimal amount) {
        return amount == null ? "0" : amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
