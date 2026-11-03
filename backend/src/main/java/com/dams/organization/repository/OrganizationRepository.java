package com.dams.organization.repository;

import com.dams.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /** rev 88 — the organization new organizations copy their master lists and Owner switches from. */
    Optional<Organization> findFirstByMasterTemplateTrue();
}
