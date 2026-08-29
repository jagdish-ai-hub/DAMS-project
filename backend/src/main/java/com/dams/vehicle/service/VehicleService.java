package com.dams.vehicle.service;

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

    public VehicleService(VehicleRepository vehicleRepo, CustomerRepository customerRepo) {
        this.vehicleRepo = vehicleRepo;
        this.customerRepo = customerRepo;
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
            return VehicleResponse.of(existing.get(), customerName(orgId, existing.get().getCustomerId()));
        }

        Vehicle v = new Vehicle();
        v.setOrgId(orgId);
        v.setCustomerId(customer.getId());
        v.setVehicleNo(normalised);
        v = vehicleRepo.save(v);
        log.info("Vehicle created: orgId={} vehicleId={} no={}", orgId, v.getId(), normalised);
        return VehicleResponse.of(v, customer.getName());
    }

    private String customerName(Long orgId, Long customerId) {
        return customerRepo.findByIdAndOrgId(customerId, orgId).map(Customer::getName).orElse(null);
    }
}
