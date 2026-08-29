package com.dams.tenant;

import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.masters.MasterType;
import com.dams.masters.dto.MasterResponse;
import com.dams.masters.entity.Bank;
import com.dams.masters.repository.BankRepository;
import com.dams.masters.service.MastersService;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the multi-tenant boundary: one organization can never see another's rows.
 * Runs the real Postgres migrations (V1–V5) in a throwaway container, so the seeded
 * demo org is org 1. Requires Docker (available in CI on ubuntu-latest).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class CrossOrgIsolationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private OrganizationRepository orgRepo;
    @Autowired
    private BankRepository bankRepo;
    @Autowired
    private MastersService mastersService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void masters_areInvisibleAcrossOrgs() {
        Organization demo = orgRepo.findAll().stream()
            .filter(o -> "JJ Motors (Demo)".equals(o.getName()))
            .findFirst()
            .orElseThrow();

        // A second dealership with one bank of its own.
        Organization other = orgRepo.save(new Organization("Second Dealership"));
        Bank otherBank = new Bank();
        otherBank.setOrgId(other.getId());
        otherBank.setName("Other-Org Bank");
        bankRepo.save(otherBank);

        // --- As a user of the demo org ---
        TenantContext.setOrgId(demo.getId());

        List<MasterResponse> demoBanks = mastersService.list(MasterType.BANKS, null);
        assertThat(demoBanks).isNotEmpty();
        assertThat(demoBanks).extracting(MasterResponse::name).doesNotContain("Other-Org Bank");

        // Fetching the other org's bank by id is a 404 for this org
        assertThatThrownBy(() -> mastersService.get(MasterType.BANKS, otherBank.getId()))
            .isInstanceOf(DamsException.class);

        // Even a bare findAll() is scoped by the Hibernate @Filter
        assertThat(bankRepo.findAll()).extracting(Bank::getName).doesNotContain("Other-Org Bank");

        // --- Switch to the other org ---
        TenantContext.setOrgId(other.getId());
        assertThat(bankRepo.findAll()).extracting(Bank::getName).containsExactly("Other-Org Bank");
    }
}
