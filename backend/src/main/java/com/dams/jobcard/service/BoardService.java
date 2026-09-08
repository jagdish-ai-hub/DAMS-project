package com.dams.jobcard.service;

import com.dams.branch.repository.BranchRepository;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.dto.RenewalRow;
import com.dams.jobcard.dto.WipRow;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only operational views over job cards (FEAT-39 renewals, FEAT-50 WIP
 * board). No writes, no workflow — visibility only, deliberately stopped
 * short of workshop management (no scheduling, no inventory).
 */
@Service
public class BoardService {

    private final JobCardRepository jobCardRepo;
    private final ClaimCloseRepository claimCloseRepo;
    private final ReceiveCategoryRepository categoryRepo;
    private final ReceiveBusinessStatusRepository statusRepo;
    private final BranchRepository branchRepo;
    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;
    private final PendingAmountCalculator pendingAmountCalculator;
    private final BranchScope branchScope;

    public BoardService(JobCardRepository jobCardRepo,
                        ClaimCloseRepository claimCloseRepo,
                        ReceiveCategoryRepository categoryRepo,
                        ReceiveBusinessStatusRepository statusRepo,
                        BranchRepository branchRepo,
                        CustomerRepository customerRepo,
                        VehicleRepository vehicleRepo,
                        PendingAmountCalculator pendingAmountCalculator,
                        BranchScope branchScope) {
        this.jobCardRepo = jobCardRepo;
        this.claimCloseRepo = claimCloseRepo;
        this.categoryRepo = categoryRepo;
        this.statusRepo = statusRepo;
        this.branchRepo = branchRepo;
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
        this.pendingAmountCalculator = pendingAmountCalculator;
        this.branchScope = branchScope;
    }

    /**
     * Vehicles sitting unbilled: open job cards (no ClaimClose, status not
     * 'Close'), oldest first. Stuck reason shows why each one isn't moving.
     */
    @Transactional(readOnly = true)
    public List<WipRow> wip() {
        Long orgId = TenantContext.requireOrgId();
        LocalDate today = LocalDate.now(com.dams.common.time.OrgTime.ZONE);
        Long closedStatusId = statusRepo.findByOrgIdAndNameIgnoreCase(orgId, "Close")
            .map(ReceiveBusinessStatus::getId).orElse(null);
        Map<Long, BigDecimal> pendingByCard =
            pendingAmountCalculator.forJobCards(orgId, jobCardRepo.findByOrgId(orgId));
        List<WipRow> out = new ArrayList<>();
        for (JobCard jc : jobCardRepo.findByOrgId(orgId)) {
            if (!branchScope.canSeeBranch(jc.getBranchId())) {
                continue;
            }
            if (closedStatusId != null && closedStatusId.equals(jc.getBusinessStatusId())) {
                continue;
            }
            if (claimCloseRepo.existsByOrgIdAndJobCardId(orgId, jc.getId())) {
                continue;
            }
            LocalDate opened = jc.getCreatedAt().atZone(com.dams.common.time.OrgTime.ZONE).toLocalDate();
            out.add(new WipRow(
                jc.getId(), reference(orgId, jc), jc.getBranchId(), branchCode(orgId, jc.getBranchId()),
                customerName(orgId, jc.getCustomerId()), vehicleNo(orgId, jc.getVehicleId()),
                categoryName(orgId, jc.getCategoryId()), statusName(orgId, jc.getBusinessStatusId()),
                opened, ChronoUnit.DAYS.between(opened, today), jc.getStuckReason(),
                pendingByCard.getOrDefault(jc.getId(), BigDecimal.ZERO)));
        }
        out.sort((a, b) -> Long.compare(b.ageDays(), a.ageDays()));
        return out;
    }

    /**
     * Upcoming service/AMC dues: job cards with a service_due_date in the
     * window (overdue + next 45 days), most overdue first. Feeds the renewal
     * reminders — a lapsed AMC is revenue walking to a competitor.
     */
    @Transactional(readOnly = true)
    public List<RenewalRow> renewals() {
        Long orgId = TenantContext.requireOrgId();
        LocalDate today = LocalDate.now(com.dams.common.time.OrgTime.ZONE);
        LocalDate horizon = today.plusDays(45);
        List<RenewalRow> out = new ArrayList<>();
        for (JobCard jc : jobCardRepo.findByOrgId(orgId)) {
            if (jc.getServiceDueDate() == null || jc.getServiceDueDate().isAfter(horizon)) {
                continue;
            }
            if (!branchScope.canSeeBranch(jc.getBranchId())) {
                continue;
            }
            var customer = customerRepo.findByIdAndOrgId(jc.getCustomerId(), orgId).orElse(null);
            out.add(new RenewalRow(
                jc.getId(), reference(orgId, jc), jc.getBranchId(), branchCode(orgId, jc.getBranchId()),
                customer == null ? "—" : customer.getName(),
                customer == null ? null : customer.getPhone(),
                vehicleNo(orgId, jc.getVehicleId()), jc.getServiceDueDate(),
                ChronoUnit.DAYS.between(today, jc.getServiceDueDate())));
        }
        out.sort((a, b) -> Long.compare(a.daysUntilDue(), b.daysUntilDue()));
        return out;
    }

    // ---------------------------------------------------------- lookups

    private String reference(Long orgId, JobCard jc) {
        return branchCode(orgId, jc.getBranchId()) + "-JC-" + jc.getId();
    }

    private String branchCode(Long orgId, Long branchId) {
        return branchRepo.findByIdAndOrgId(branchId, orgId).map(b -> b.getCode()).orElse("?");
    }

    private String customerName(Long orgId, Long customerId) {
        return customerRepo.findByIdAndOrgId(customerId, orgId).map(c -> c.getName()).orElse("—");
    }

    private String vehicleNo(Long orgId, Long vehicleId) {
        if (vehicleId == null) {
            return null;
        }
        return vehicleRepo.findByIdAndOrgId(vehicleId, orgId).map(v -> v.getVehicleNo()).orElse(null);
    }

    private String categoryName(Long orgId, Long categoryId) {
        return categoryRepo.findByIdAndOrgId(categoryId, orgId).map(c -> c.getName()).orElse("—");
    }

    private String statusName(Long orgId, Long statusId) {
        return statusRepo.findByIdAndOrgId(statusId, orgId).map(s -> s.getName()).orElse("—");
    }
}
