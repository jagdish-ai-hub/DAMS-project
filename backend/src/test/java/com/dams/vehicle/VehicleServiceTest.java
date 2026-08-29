package com.dams.vehicle;

import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.vehicle.dto.VehicleRequest;
import com.dams.vehicle.dto.VehicleResponse;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import com.dams.vehicle.service.VehicleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    private static final long ORG = 1L;

    @Mock private VehicleRepository vehicleRepo;
    @Mock private CustomerRepository customerRepo;

    private VehicleService service;

    @BeforeEach
    void setUp() {
        service = new VehicleService(vehicleRepo, customerRepo);
        TenantContext.setOrgId(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_normalisesNumber_strippingSpacesAndLowercase() {
        when(customerRepo.findByIdAndOrgId(42L, ORG)).thenReturn(Optional.of(customer(42L)));
        when(vehicleRepo.findByOrgIdAndVehicleNo(ORG, "OD05CA4177")).thenReturn(Optional.empty());
        when(vehicleRepo.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

        VehicleRequest req = new VehicleRequest();
        req.setCustomerId(42L);
        req.setVehicleNo(" od05 ca 4177 ");

        VehicleResponse result = service.create(req);

        assertThat(result.vehicleNo()).isEqualTo("OD05CA4177");
        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepo).save(captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(ORG);
        assertThat(captor.getValue().getVehicleNo()).isEqualTo("OD05CA4177");
    }

    @Test
    void create_whenNumberAlreadyExists_returnsExistingRow_noDuplicate() {
        Vehicle existing = new Vehicle();
        ReflectionTestUtils.setField(existing, "id", 9L);
        existing.setOrgId(ORG);
        existing.setCustomerId(42L);
        existing.setVehicleNo("OD05CA4177");

        when(customerRepo.findByIdAndOrgId(42L, ORG)).thenReturn(Optional.of(customer(42L)));
        when(vehicleRepo.findByOrgIdAndVehicleNo(ORG, "OD05CA4177")).thenReturn(Optional.of(existing));

        VehicleRequest req = new VehicleRequest();
        req.setCustomerId(42L);
        req.setVehicleNo("OD05CA4177");

        VehicleResponse result = service.create(req);

        assertThat(result.id()).isEqualTo(9L);
        verify(vehicleRepo, never()).save(any());
    }

    private static Customer customer(long id) {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", id);
        c.setOrgId(ORG);
        c.setName("Test Customer");
        return c;
    }
}
