package com.dams.db;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Migration health plus the V21 regression: a REJECTED receive document keeps
 * settled=false but must not trip the one-open-per-job-card index when the clean
 * new draft is inserted. Needs real Postgres (partial indexes) — skipped without
 * Docker, runs in CI like {@link com.dams.tenant.CrossOrgIsolationTest}.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MigrationV21Test {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private OrganizationRepository orgRepo;
    @Autowired private BranchRepository branchRepo;
    @Autowired private CustomerRepository customerRepo;
    @Autowired private ReceiveCategoryRepository categoryRepo;
    @Autowired private ReceiveBusinessStatusRepository statusRepo;
    @Autowired private JobCardRepository jobCardRepo;
    @Autowired private ReceiveDocumentRepository receiveDocumentRepo;
    @Autowired private AppUserRepository userRepo;

    @Test
    void contextBoots_migrationsApplyCleanly() {
        // Booting this context runs every Flyway migration (V1–V22) — failure fails the test.
        assertThat(orgRepo.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void rejectedDocument_doesNotBlockCleanNewDraft() {
        Organization org = orgRepo.save(new Organization("Migration Test Org"));
        Long orgId = org.getId();
        Branch branch = branch();
        branch.setOrgId(orgId);
        branch = branchRepo.save(branch);
        AppUser cashier = new AppUser();
        cashier.setOrganization(org);
        cashier.setName("Migration Cashier");
        cashier.setEmail("migration-v21-test@example.com");
        cashier.setRole(Role.CASHIER);
        cashier.setHomeBranchId(branch.getId());
        cashier = userRepo.save(cashier);
        Customer customer = new Customer();
        customer.setOrgId(orgId);
        customer.setName("Migration Customer");
        customer = customerRepo.save(customer);
        ReceiveCategory category = new ReceiveCategory();
        category.setOrgId(orgId);
        category.setName("Workshop");
        category = categoryRepo.save(category);
        ReceiveBusinessStatus status = new ReceiveBusinessStatus();
        status.setOrgId(orgId);
        status.setName("Open");
        status = statusRepo.save(status);
        JobCard jc = new JobCard();
        jc.setOrgId(orgId);
        jc.setBranchId(branch.getId());
        jc.setCustomerId(customer.getId());
        jc.setCategoryId(category.getId());
        jc.setBusinessStatusId(status.getId());
        jc = jobCardRepo.save(jc);

        ReceiveDocument rejected = doc(orgId, branch.getId(), jc.getId(), cashier.getId(),
            WorkflowStatus.REJECTED, "TST-JUL26-R-001");
        ReceiveDocument draft = doc(orgId, branch.getId(), jc.getId(), cashier.getId(),
            WorkflowStatus.DRAFT, null);

        assertThatNoException().isThrownBy(() -> {
            receiveDocumentRepo.saveAndFlush(rejected);
            receiveDocumentRepo.saveAndFlush(draft);
        });
        assertThat(receiveDocumentRepo.existsByOrgId(orgId)).isTrue();
    }

    private static Branch branch() {
        Branch branch = new Branch();
        branch.setName("Test Branch");
        branch.setCode("TST");
        branch.setActive(true);
        return branch;
    }

    private static ReceiveDocument doc(Long orgId, Long branchId, Long jobCardId, Long createdBy,
                                       WorkflowStatus status, String documentNo) {
        ReceiveDocument doc = new ReceiveDocument();
        doc.setOrgId(orgId);
        doc.setBranchId(branchId);
        doc.setJobCardId(jobCardId);
        doc.setWorkflowStatus(status);
        doc.setSettled(false);
        doc.setDocumentNo(documentNo);
        doc.setCreatedBy(createdBy);
        return doc;
    }
}
