package com.dams.vehicle.service;

import com.dams.audit.entity.EventType;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.vehicle.dto.VehicleRequest;
import com.dams.vehicle.dto.VehicleResponse;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Vehicle lookup and create. The vehicle number is the natural key: it is normalised
 * (uppercase, alphanumerics only) and a create that collides with an existing number
 * returns that row rather than a duplicate — the dedup AGENT.md asks for.
 */
@Service
public class VehicleService {

    private static final Logger log = LoggerFactory.getLogger(VehicleService.class);

    private final VehicleRepository vehicleRepo;
    private final CustomerRepository customerRepo;
    private final com.dams.audit.service.AuditService auditService;
    private final com.dams.common.security.BranchScope branchScope;

    public VehicleService(VehicleRepository vehicleRepo, CustomerRepository customerRepo,
                          com.dams.audit.service.AuditService auditService,
                          com.dams.common.security.BranchScope branchScope) {
        this.vehicleRepo = vehicleRepo;
        this.customerRepo = customerRepo;
        this.auditService = auditService;
        this.branchScope = branchScope;
    }

    /** Lookup by (already-typed) number. Returns empty when nothing matches. */
    @Transactional(readOnly = true)
    public Optional<VehicleResponse> findByNumber(String rawNumber) {
        Long orgId = TenantContext.requireOrgId();
        String normalised = Vehicle.normalise(rawNumber);
        if (normalised == null || normalised.isBlank()) {
            return Optional.empty();
        }
        return vehicleRepo.findByOrgIdAndVehicleNo(orgId, normalised)
            .map(v -> VehicleResponse.of(v, customerName(orgId, v.getCustomerId())));
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        return createOrGet(request).response();
    }

    /** Idempotent create: {@code created} is false when the number already existed (dedup hit). */
    @Transactional
    public CreateResult createOrGet(VehicleRequest request) {
        Long orgId = TenantContext.requireOrgId();
        String normalised = Vehicle.normalise(request.getVehicleNo());
        if (normalised == null || normalised.isBlank()) {
            throw DamsException.badRequest("Vehicle number must contain at least one letter or digit");
        }

        Customer customer = customerRepo.findByIdAndOrgId(request.getCustomerId(), orgId)
            .orElseThrow(() -> DamsException.notFound("Customer", request.getCustomerId()));

        // Dedup: an existing number wins, no duplicate row.
        Optional<Vehicle> existing = vehicleRepo.findByOrgIdAndVehicleNo(orgId, normalised);
        if (existing.isPresent()) {
            return new CreateResult(
                VehicleResponse.of(existing.get(), customerName(orgId, existing.get().getCustomerId())), false);
        }

        Vehicle v = new Vehicle();
        v.setOrgId(orgId);
        v.setCustomerId(customer.getId());
        v.setVehicleNo(normalised);
        v = vehicleRepo.save(v);
        log.info("Vehicle created: orgId={} vehicleId={} no={}", orgId, v.getId(), normalised);
        return new CreateResult(VehicleResponse.of(v, customer.getName()), true);
    }

    /**
     * Owner correction of a vehicle's number (rev 77). The number is normalised like every other
     * entry and stays unique per organisation: a number another vehicle already holds is refused.
     */
    @Transactional
    public VehicleResponse rename(Long id, String rawNumber) {
        Long orgId = TenantContext.requireOrgId();
        Vehicle v = vehicleRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Vehicle", id));
        String normalised = Vehicle.normalise(rawNumber);
        if (normalised == null || normalised.isBlank()) {
            throw DamsException.badRequest("Vehicle number must contain at least one letter or digit");
        }
        Optional<Vehicle> clash = vehicleRepo.findByOrgIdAndVehicleNo(orgId, normalised);
        if (clash.isPresent() && !clash.get().getId().equals(v.getId())) {
            throw DamsException.conflict("Vehicle number " + normalised + " already belongs to another vehicle");
        }
        String before = v.getVehicleNo();
        v.setVehicleNo(normalised);
        v = vehicleRepo.save(v);
        log.info("Vehicle renamed: orgId={} vehicleId={} {} -> {}", orgId, v.getId(), before, normalised);
        return VehicleResponse.of(v, customerName(orgId, v.getCustomerId()));
    }

    /**
     * Owner moves a vehicle to another customer (rev 77). Only who owns it from now on changes:
     * job cards, receipts and expenses already recorded stay with the customer they were recorded
     * for, so no balance is rewritten. Audited as VEHICLE_MOVED (who, from, to).
     */
    @Transactional
    public VehicleResponse move(Long id, Long toCustomerId) {
        Long orgId = TenantContext.requireOrgId();
        Vehicle v = vehicleRepo.findByIdAndOrgId(id, orgId)
            .orElseThrow(() -> DamsException.notFound("Vehicle", id));
        Customer to = customerRepo.findByIdAndOrgId(toCustomerId, orgId)
            .orElseThrow(() -> DamsException.notFound("Customer", toCustomerId));
        Long fromId = v.getCustomerId();
        if (to.getId().equals(fromId)) {
            return VehicleResponse.of(v, to.getName());   // already theirs — nothing to do
        }
        v.setCustomerId(to.getId());
        v = vehicleRepo.save(v);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("vehicleNo", v.getVehicleNo());
        detail.put("fromCustomerId", fromId);
        detail.put("fromCustomerName", customerName(orgId, fromId));
        detail.put("toCustomerId", to.getId());
        detail.put("toCustomerName", to.getName());
        auditService.recordUserEvent("Vehicle", v.getId(), EventType.VEHICLE_MOVED, branchScope.currentUserId(), detail);
        log.info("Vehicle moved: orgId={} vehicleId={} no={} {} -> {}", orgId, v.getId(), v.getVehicleNo(), fromId, to.getId());
        return VehicleResponse.of(v, to.getName());
    }

    public record CreateResult(VehicleResponse response, boolean created) {}

    private String customerName(Long orgId, Long customerId) {
        return customerRepo.findByIdAndOrgId(customerId, orgId).map(Customer::getName).orElse(null);
    }
}
