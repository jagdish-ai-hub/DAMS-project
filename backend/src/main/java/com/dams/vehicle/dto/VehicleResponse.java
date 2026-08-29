package com.dams.vehicle.dto;

import com.dams.vehicle.entity.Vehicle;

import java.time.Instant;

public record VehicleResponse(
    Long id,
    String vehicleNo,
    Long customerId,
    String customerName,
    Instant createdAt
) {
    public static VehicleResponse of(Vehicle v, String customerName) {
        return new VehicleResponse(v.getId(), v.getVehicleNo(), v.getCustomerId(), customerName, v.getCreatedAt());
    }
}
