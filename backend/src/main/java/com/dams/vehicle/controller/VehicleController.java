package com.dams.vehicle.controller;

import com.dams.common.exception.DamsException;
import com.dams.vehicle.dto.VehicleRequest;
import com.dams.vehicle.dto.VehicleResponse;
import com.dams.vehicle.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Vehicle lookup + create. Create is idempotent on the normalised number — an existing
 * vehicle is returned with 200, a new one with 201.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicles", description = "Vehicle lookup and registration")
@SecurityRequirement(name = "bearerAuth")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    @Operation(summary = "Look up a vehicle by number (?vehicle_no=)")
    public VehicleResponse byNumber(@RequestParam(name = "vehicle_no") String vehicleNo) {
        return vehicleService.findByNumber(vehicleNo)
            .orElseThrow(() -> DamsException.notFound("Vehicle", "number", vehicleNo));
    }

    @PostMapping
    @Operation(summary = "Register a vehicle for a customer (deduped on the number)")
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest request) {
        VehicleResponse result = vehicleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
