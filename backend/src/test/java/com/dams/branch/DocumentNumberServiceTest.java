package com.dams.branch;

import com.dams.branch.entity.Branch;
import com.dams.branch.entity.DocType;
import com.dams.branch.repository.BranchRepository;
import com.dams.branch.service.DocumentNumberService;
import com.dams.config.TenantContext;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Document numbering is money-adjacent — AGENT.md requires a test that it is "gap-free,
 * sequential, never reused". The gap-free guarantee lives in an atomic
 * INSERT ... ON CONFLICT ... RETURNING, so this runs against a real Postgres (Testcontainers,
 * CI — skipped locally without Docker, like CrossOrgIsolationTest).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DocumentNumberServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private DocumentNumberService documentNumberService;
    @Autowired private OrganizationRepository orgRepo;
    @Autowired private BranchRepository branchRepo;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void numbers_areSequentialGapFree_andFormatted() {
        Organization demo = orgRepo.findAll().stream()
            .filter(o -> "JJ Motors (Demo)".equals(o.getName())).findFirst().orElseThrow();
        TenantContext.setOrgId(demo.getId());
        Branch branch = branchRepo.findByOrgIdOrderByCodeAsc(demo.getId()).stream()
            .filter(b -> "OOR".equals(b.getCode())).findFirst().orElseThrow();

        String first = documentNumberService.nextNumber(demo.getId(), branch, DocType.R);
        String second = documentNumberService.nextNumber(demo.getId(), branch, DocType.R);
        String third = documentNumberService.nextNumber(demo.getId(), branch, DocType.R);

        assertThat(first).matches("OOR-[A-Z]{3}\\d{2}-R-\\d{3}");   // OOR-<MON><YY>-R-NNN
        int a = seqOf(first);
        int b = seqOf(second);
        int c = seqOf(third);
        assertThat(b).isEqualTo(a + 1);
        assertThat(c).isEqualTo(b + 1);

        // A different type has its own independent counter.
        String expense = documentNumberService.nextNumber(demo.getId(), branch, DocType.E);
        assertThat(expense).contains("-E-");
        assertThat(seqOf(expense)).isEqualTo(1);
    }

    private static int seqOf(String documentNo) {
        String[] parts = documentNo.split("-");
        return Integer.parseInt(parts[parts.length - 1]);
    }
}
