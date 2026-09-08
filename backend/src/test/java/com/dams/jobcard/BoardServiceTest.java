package com.dams.jobcard;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.BoardService;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

/**
 * FEAT-39/50: renewals surface dated dues; the WIP board surfaces stuck,
 * unbilled work oldest first. Both read-only over existing job cards.
 */
@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    private static final long ORG = 1L;
    private static final long BRANCH = 3L;

    @Mock private JobCardRepository jobCardRepo;
    @Mock private ClaimCloseRepository claimCloseRepo;
    @Mock private ReceiveCategoryRepository categoryRepo;
    @Mock private ReceiveBusinessStatusRepository statusRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private PendingAmountCalculator pendingAmountCalculator;
    @Mock private BranchScope branchScope;

    private BoardService service;

    @BeforeEach
    void setUp() {
        service = new BoardService(jobCardRepo, claimCloseRepo, categoryRepo, statusRepo,
            branchRepo, customerRepo, vehicleRepo, pendingAmountCalculator, branchScope);
        TenantContext.setOrgId(ORG);
        lenient().when(branchScope.canSeeBranch(BRANCH)).thenReturn(true);
        lenient().when(branchRepo.findByIdAndOrgId(BRANCH, ORG)).thenReturn(Optional.of(branch()));
        lenient().when(customerRepo.findByIdAndOrgId(5L, ORG)).thenReturn(Optional.of(customer()));
        lenient().when(vehicleRepo.findByIdAndOrgId(9L, ORG)).thenReturn(Optional.of(vehicle()));
        lenient().when(categoryRepo.findByIdAndOrgId(1L, ORG)).thenReturn(Optional.of(category()));
        lenient().when(statusRepo.findByIdAndOrgId(2L, ORG)).thenReturn(Optional.of(status()));
        lenient().when(statusRepo.findByOrgIdAndNameIgnoreCase(ORG, "Close")).thenReturn(Optional.empty());
        lenient().when(claimCloseRepo.existsByOrgIdAndJobCardId(ORG, 11L)).thenReturn(false);
        lenient().when(pendingAmountCalculator.forJobCards(eq(ORG), org.mockito.ArgumentMatchers.anyList()))
            .thenReturn(Map.of(11L, new BigDecimal("4000")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void wip_listsOpenJobs_oldestFirst_withStuckReason() {
        JobCard stuck = jobCard(11L, 20);
        stuck.setStuckReason("Waiting for Eicher approval");
        JobCard fresh = jobCard(12L, 2);
        when(jobCardRepo.findByOrgId(ORG)).thenReturn(List.of(fresh, stuck));
        when(claimCloseRepo.existsByOrgIdAndJobCardId(ORG, 12L)).thenReturn(false);

        var rows = service.wip();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).jobCardId()).isEqualTo(11L);
        assertThat(rows.get(0).ageDays()).isEqualTo(20L);
        assertThat(rows.get(0).stuckReason()).isEqualTo("Waiting for Eicher approval");
        assertThat(rows.get(0).pendingAmount()).isEqualByComparingTo("4000");
    }

    @Test
    void renewals_surfacesOverdueAndUpcoming_only() {
        JobCard overdue = jobCard(11L, 20);
        overdue.setServiceDueDate(LocalDate.now().minusDays(3));
        JobCard upcoming = jobCard(12L, 2);
        upcoming.setServiceDueDate(LocalDate.now().plusDays(10));
        JobCard far = jobCard(13L, 2);
        far.setServiceDueDate(LocalDate.now().plusDays(90));
        JobCard none = jobCard(14L, 2);
        when(jobCardRepo.findByOrgId(ORG)).thenReturn(List.of(upcoming, far, none, overdue));

        var rows = service.renewals();

        assertThat(rows).extracting(r -> r.jobCardId()).containsExactly(11L, 12L);
        assertThat(rows.get(0).daysUntilDue()).isEqualTo(-3L);
        assertThat(rows.get(0).customerPhone()).isEqualTo("9876543210");
    }

    // ---------------------------------------------------------- fixtures

    private static JobCard jobCard(Long id, int ageDays) {
        JobCard j = new JobCard();
        ReflectionTestUtils.setField(j, "id", id);
        j.setOrgId(ORG);
        j.setBranchId(BRANCH);
        j.setCustomerId(5L);
        j.setVehicleId(9L);
        j.setCategoryId(1L);
        j.setBusinessStatusId(2L);
        ReflectionTestUtils.setField(j, "createdAt",
            Instant.now().minus(ageDays, java.time.temporal.ChronoUnit.DAYS));
        return j;
    }

    private static Branch branch() {
        Branch b = new Branch();
        ReflectionTestUtils.setField(b, "id", BRANCH);
        b.setOrgId(ORG);
        b.setCode("OOR");
        b.setName("Rayagada");
        return b;
    }

    private static Customer customer() {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", 5L);
        c.setOrgId(ORG);
        c.setName("Sharma Transport");
        c.setPhone("9876543210");
        return c;
    }

    private static Vehicle vehicle() {
        Vehicle v = new Vehicle();
        ReflectionTestUtils.setField(v, "id", 9L);
        v.setVehicleNo("OD05CA4177");
        return v;
    }

    private static ReceiveCategory category() {
        ReceiveCategory c = new ReceiveCategory();
        ReflectionTestUtils.setField(c, "id", 1L);
        c.setName("Workshop");
        return c;
    }

    private static ReceiveBusinessStatus status() {
        ReceiveBusinessStatus s = new ReceiveBusinessStatus();
        ReflectionTestUtils.setField(s, "id", 2L);
        s.setName("WIP");
        return s;
    }
}
