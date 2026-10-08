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
    Instant createdAt,
    String lastContactPhone   // last contact number saved on one of their receipts; null if none (rev 68)
) {
    public record VehicleRef(Long id, String vehicleNo) {}

    public static CustomerResponse of(Customer c, List<VehicleRef> vehicles) {
        return of(c, vehicles, null);
    }

    public static CustomerResponse of(Customer c, List<VehicleRef> vehicles, String lastContactPhone) {
        return new CustomerResponse(c.getId(), c.getName(), c.getPhone(), vehicles, c.getCreatedAt(),
            lastContactPhone);
    }
}
