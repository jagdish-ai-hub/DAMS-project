package com.dams.customer.dto;

import com.dams.customer.entity.Customer;

import java.time.Instant;
import java.util.List;

/**
 * A customer plus their vehicle numbers. Used by the picker (?q=) and by GET
 * /customers/{id}.
 */
public record CustomerResponse(
    Long id,
    String name,
    String phone,
    List<VehicleRef> vehicles,
    Instant createdAt
) {
    public record VehicleRef(Long id, String vehicleNo) {}

    public static CustomerResponse of(Customer c, List<VehicleRef> vehicles) {
        return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), vehicles, c.getCreatedAt());
    }
}
