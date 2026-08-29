package com.dams.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create payload for a vehicle. {@code vehicleNo} is normalised server-side (uppercase,
 * alphanumerics only); if one already exists for the org, that row is returned as-is.
 */
@Getter
@Setter
@NoArgsConstructor
public class VehicleRequest {

    @NotNull(message = "customerId is required")
    private Long customerId;

    @NotBlank(message = "Vehicle number is required")
    @Size(max = 20, message = "Vehicle number must be at most 20 characters")
    private String vehicleNo;
}
