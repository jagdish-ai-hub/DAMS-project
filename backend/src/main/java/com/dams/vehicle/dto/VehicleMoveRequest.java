package com.dams.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** rev 77 — the Owner moves a vehicle to another customer. */
@Getter
@Setter
@NoArgsConstructor
public class VehicleMoveRequest {
    @NotNull(message = "customerId is required")
    private Long customerId;
}
