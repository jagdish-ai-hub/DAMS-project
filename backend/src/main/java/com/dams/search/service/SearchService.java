package com.dams.search.service;

import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.search.dto.SearchResponse;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Universal search behind {@code GET /api/v1/search?q=} (cashier-home.html). Matches a
 * customer by name / phone, a vehicle by number, or a job card by internal id /
 * invoice_no / dbm_id, and returns one hit per customer.
 *
 * Branch scope ({@link BranchScope}): job-card / invoice matches and every customer's
 * job-card roll-up are limited to the branches the caller may see — for a CASHIER that is
 * their home branch unless the org's multi_branch_cashier_access toggle is on. Name /
 * phone / vehicle matches are org-wide because customers and vehicles are not
 * branch-scoped.
 */
@Service
public class SearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_HITS = 15;

    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;
    private final JobCardRepository jobCardRepo;
    private final BranchScope branchScope;
    private final PendingAmountCalculator pendingAmountCalculator;

    public SearchService(CustomerRepository customerRepo,
                         VehicleRepository vehicleRepo,
                         JobCardRepository jobCardRepo,
                         BranchScope branchScope,
                         PendingAmountCalculator pendingAmountCalculator) {
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
        this.jobCardRepo = jobCardRepo;
        this.branchScope = branchScope;
        this.pendingAmountCalculator = pendingAmountCalculator;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.length() < MIN_QUERY_LENGTH) {
            return new SearchResponse(q, List.of());
        }

        Long orgId = TenantContext.requireOrgId();
        Optional<Set<Long>> allowedBranches = branchScope.allowedBranchIds();

        // customerId -> which field matched (first match wins, in priority order)
        Map<Long, String> matchByCustomer = new LinkedHashMap<>();

        // 1. name / phone (org-wide)
        String lowerQ = q.toLowerCase();
        for (Customer c : customerRepo.search(orgId, q, Limit.of(MAX_HITS * 2))) {
            boolean nameHit = c.getName() != null && c.getName().toLowerCase().contains(lowerQ);
            matchByCustomer.putIfAbsent(c.getId(), nameHit ? "Name" : "Phone");
        }

        // 2. vehicle number (org-wide)
        String normalisedNo = Vehicle.normalise(q);
        if (normalisedNo != null && !normalisedNo.isBlank()) {
            for (Vehicle v : vehicleRepo.findByOrgIdAndVehicleNoContainingOrderByVehicleNoAsc(orgId, normalisedNo)) {
                matchByCustomer.putIfAbsent(v.getCustomerId(), "Vehicle");
            }
        }

        // 3. job card: internal id / invoice_no / dbm_id (branch-scoped)
        Long idQ = parseLong(q);
        for (JobCard j : jobCardRepo.search(orgId, q, idQ)) {
            if (!branchAllowed(allowedBranches, j.getBranchId())) {
                continue;
            }
            boolean invoiceHit = j.getInvoiceNo() != null && j.getInvoiceNo().toLowerCase().contains(lowerQ);
            matchByCustomer.putIfAbsent(j.getCustomerId(), invoiceHit ? "Invoice" : "Job Card");
        }

        if (matchByCustomer.isEmpty()) {
            return new SearchResponse(q, List.of());
        }

        List<Long> customerIds = matchByCustomer.keySet().stream().limit(MAX_HITS).toList();
        Map<Long, Customer> customers = customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId, customerIds).stream()
            .collect(Collectors.toMap(Customer::getId, c -> c));

        Map<Long, List<String>> vehiclesByCustomer = vehicleRepo
            .findByOrgIdAndCustomerIdInOrderByVehicleNoAsc(orgId, customerIds).stream()
            .collect(Collectors.groupingBy(Vehicle::getCustomerId,
                Collectors.mapping(Vehicle::getVehicleNo, Collectors.toList())));

        // job-card roll-up per customer, branch-scoped
        Map<Long, int[]> countByCustomer = new java.util.HashMap<>();
        Map<Long, BigDecimal> invoicedByCustomer = new java.util.HashMap<>();
        Map<Long, BigDecimal> outstandingByCustomer = new java.util.HashMap<>();
        for (JobCard j : jobCardRepo.findByOrgIdAndCustomerIdInOrderByCreatedAtDesc(orgId, customerIds)) {
            if (!branchAllowed(allowedBranches, j.getBranchId())) {
                continue;
            }
            countByCustomer.computeIfAbsent(j.getCustomerId(), k -> new int[1])[0]++;
            if (j.getInvoiceAmount() != null) {
                invoicedByCustomer.merge(j.getCustomerId(), j.getInvoiceAmount(), BigDecimal::add);
            }
            outstandingByCustomer.merge(j.getCustomerId(), pendingAmountCalculator.forJobCard(j), BigDecimal::add);
        }

        List<SearchResponse.Hit> hits = new ArrayList<>();
        for (Long id : customerIds) {
            Customer c = customers.get(id);
            if (c == null) {
                continue;
            }
            hits.add(new SearchResponse.Hit(
                c.getId(),
                c.getName(),
                c.getPhone(),
                vehiclesByCustomer.getOrDefault(id, List.of()),
                countByCustomer.getOrDefault(id, new int[1])[0],
                invoicedByCustomer.getOrDefault(id, BigDecimal.ZERO),
                outstandingByCustomer.getOrDefault(id, BigDecimal.ZERO),
                matchByCustomer.get(id)));
        }
        hits.sort((a, b) -> a.customerName().compareToIgnoreCase(b.customerName()));

        return new SearchResponse(q, hits);
    }

    private static boolean branchAllowed(Optional<Set<Long>> allowed, Long branchId) {
        return allowed.map(set -> set.contains(branchId)).orElse(true);
    }

    private static Long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
