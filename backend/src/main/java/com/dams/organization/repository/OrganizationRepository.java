package com.dams.organization.repository;

import com.dams.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /** Orgs that opted into the nightly owner digest (FEAT-42). */
    java.util.List<Organization> findByDigestEnabledTrueAndActiveTrue();
}
