package com.dams.organization.service;

import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.organization.dto.OrgSettingsRequest;
import com.dams.organization.dto.OrgSettingsResponse;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's own organization settings (name, multi-branch cashier access). Org-scoped —
 * distinct from the Super Admin cross-org endpoints in com.dams.admin.
 */
@Service
public class OrgSettingsService {

    private static final Logger log = LoggerFactory.getLogger(OrgSettingsService.class);

    private final OrganizationRepository orgRepo;

    public OrgSettingsService(OrganizationRepository orgRepo) {
        this.orgRepo = orgRepo;
    }

    @Transactional(readOnly = true)
    public OrgSettingsResponse get() {
        return OrgSettingsResponse.of(load());
    }

    @Transactional
    public OrgSettingsResponse update(OrgSettingsRequest request) {
        Organization org = load();

        if (request.getName() != null && !request.getName().isBlank()) {
            org.setName(request.getName().trim());
        }
        if (request.getMultiBranchCashierAccess() != null) {
            org.setMultiBranchCashierAccess(request.getMultiBranchCashierAccess());
        }
        orgRepo.save(org);

        log.info("Org settings updated: orgId={} multiBranchCashierAccess={}",
            org.getId(), org.isMultiBranchCashierAccess());
        return OrgSettingsResponse.of(org);
    }

    private Organization load() {
        Long orgId = TenantContext.requireOrgId();
        return orgRepo.findById(orgId)
            .orElseThrow(() -> DamsException.notFound("Organization", orgId));
    }
}
