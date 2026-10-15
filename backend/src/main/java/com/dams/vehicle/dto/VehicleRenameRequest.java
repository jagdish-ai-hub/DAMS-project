package com.dams.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** rev 77 — the Owner corrects a vehicle's number from Masters → Customers. */
@Getter
@Setter
@NoArgsConstructor
public class VehicleRenameRequest {
    @NotBlank(message = "Vehicle number is required")
    @Size(max = 20, message = "Vehicle number must be at most 20 characters")
    private String vehicleNo;
}
