package com.dams.vehicle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * A vehicle belonging to one customer. {@code vehicleNo} is the natural key — normalised
 * to uppercase with non-alphanumerics stripped — and is unique per org. Creating a
 * vehicle that already exists (same normalised number) returns the existing row rather
 * than a duplicate. See AGENT.md ("Vehicle number").
 */
@Entity
@Table(name = "vehicle")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, updatable = false)
    private Long orgId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "vehicle_no", nullable = false, length = 20)
    private String vehicleNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Uppercase, keep only A-Z and 0-9. */
    public static String normalise(String raw) {
        return raw == null ? null : raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
