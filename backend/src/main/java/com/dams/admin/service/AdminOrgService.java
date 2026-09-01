package com.dams.admin.service;

import com.dams.common.exception.DamsException;
import com.dams.email.EmailService;
import com.dams.masters.service.MasterProvisioningService;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Super Admin operations — intentionally in their own package because these are the ONLY
 * queries that intentionally run cross-org. Every other service is org-scoped.
 * See AGENT.md — Multi-tenancy.
 */
@Service
public class AdminOrgService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrgService.class);

    /**
     * Invite tokens expire after 7 days — long enough for the owner to respond,
     * short enough to limit the window if the link leaks.
     */
    private static final int INVITE_EXPIRY_DAYS = 7;

    private final OrganizationRepository orgRepo;
    private final AppUserRepository userRepo;
    private final EmailService emailService;
    private final OrganizationPurgeService purgeService;
    private final MasterProvisioningService masterProvisioningService;

    /** Frontend origin used to build the accept-invite link. */
    private final String appBaseUrl;

    public AdminOrgService(OrganizationRepository orgRepo,
                           AppUserRepository userRepo,
                           EmailService emailService,
                           OrganizationPurgeService purgeService,
                           MasterProvisioningService masterProvisioningService,
                           @Value("${dams.app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.orgRepo = orgRepo;
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.purgeService = purgeService;
        this.masterProvisioningService = masterProvisioningService;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * List all organizations. Cross-org query — SUPER_ADMIN only.
     */
    @Transactional(readOnly = true)
    public List<Organization> listOrganizations() {
        return orgRepo.findAll();
    }

    /**
     * Get one organization by id.
     */
    @Transactional(readOnly = true)
    public Organization getOrganization(Long id) {
        return orgRepo.findById(id)
            .orElseThrow(() -> DamsException.notFound("Organization", id));
    }

    /**
     * Create a new organization and invite its first Owner.
     *
     * Steps:
     *  1. Reject if the owner email is already taken (before creating the org, so no orphan row).
     *  2. Persist the org.
     *  3. Create an AppUser(OWNER) with a UUID invite token and no password yet.
     *  4. Hand the invite link to EmailService (v1: logged, not emailed).
     *
     * The invite link is also returned to the caller so the Super Admin panel can display it.
     */
    @Transactional
    public OrgCreationResult createOrganization(String orgName, String ownerName, String ownerEmail) {
        if (userRepo.findByEmail(ownerEmail).isPresent()) {
            throw DamsException.conflict(
                "AppUser with email '" + ownerEmail + "' already exists");
        }

        Organization org = new Organization(orgName);
        org = orgRepo.save(org);

        // Ship the standard master catalogue so the new Owner has working dropdowns on day one.
        masterProvisioningService.provisionDefaults(org.getId());

        String inviteToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(INVITE_EXPIRY_DAYS, ChronoUnit.DAYS);

        AppUser owner = new AppUser();
        owner.setOrganization(org);
        owner.setName(ownerName);
        owner.setEmail(ownerEmail);
        owner.setRole(Role.OWNER);
        owner.setActive(true);
        owner.setInviteToken(inviteToken);
        owner.setInviteExpiresAt(expiresAt);
        userRepo.save(owner);

        String inviteLink = buildInviteLink(inviteToken);

        log.info("Org created: orgId={} orgName='{}' ownerUserId={} ownerEmail='{}' inviteExpiresAt={}",
            org.getId(), orgName, owner.getId(), ownerEmail, expiresAt);

        emailService.sendOwnerInvite(ownerEmail, orgName, inviteLink);

        return new OrgCreationResult(org.getId(), orgName, inviteToken, inviteLink);
    }

    /**
     * Toggle the active flag on an organization.
     */
    @Transactional
    public Organization toggleActive(Long id, boolean active) {
        Organization org = orgRepo.findById(id)
            .orElseThrow(() -> DamsException.notFound("Organization", id));

        org.setActive(active);
        orgRepo.save(org);

        log.info("Org active toggled: orgId={} active={}", id, active);

        return org;
    }

    /**
     * Permanently delete an organization and everything under it (branches, users, masters,
     * receivers). Super Admin only — for removing a dealership onboarded by mistake.
     *
     * Stage 4+ will refuse this once the org has any transactional documents; there are none
     * yet, so no guard is needed here.
     */
    @Transactional
    public void deleteOrganization(Long id) {
        Organization org = orgRepo.findById(id)
            .orElseThrow(() -> DamsException.notFound("Organization", id));

        purgeService.purgeChildren(org.getId());
        orgRepo.delete(org);

        log.info("Org deleted: orgId={} orgName='{}'", id, org.getName());
    }

    private String buildInviteLink(String inviteToken) {
        return UriComponentsBuilder.fromUriString(appBaseUrl)
            .path("/accept-invite")
            .queryParam("token", inviteToken)
            .build()
            .toUriString();
    }

    /**
     * Value returned after org creation. inviteLink is the full URL the owner opens;
     * inviteToken is included too for callers/tests that need the raw value.
     */
    public record OrgCreationResult(Long orgId, String orgName, String inviteToken, String inviteLink) {}
}
