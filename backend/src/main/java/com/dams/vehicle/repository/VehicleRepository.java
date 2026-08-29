package com.dams.vehicle.repository;

import com.dams.vehicle.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByIdAndOrgId(Long id, Long orgId);

    /** Dedup key: vehicle_no is unique per org (already normalised by the caller). */
    Optional<Vehicle> findByOrgIdAndVehicleNo(Long orgId, String vehicleNo);

    List<Vehicle> findByOrgIdAndCustomerIdOrderByVehicleNoAsc(Long orgId, Long customerId);

    List<Vehicle> findByOrgIdAndCustomerIdInOrderByVehicleNoAsc(Long orgId, Collection<Long> customerIds);

    /** Universal search — contains-match on the normalised number. */
    List<Vehicle> findByOrgIdAndVehicleNoContainingOrderByVehicleNoAsc(Long orgId, String vehicleNoFragment);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
