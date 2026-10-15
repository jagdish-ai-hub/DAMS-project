package com.dams.vehicle;

import com.dams.common.exception.DamsException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    private static final long ORG = 1L;

    @Mock private VehicleRepository vehicleRepo;
    @Mock private CustomerRepository customerRepo;
    @Mock private com.dams.audit.service.AuditService auditService;
    @Mock private com.dams.common.security.BranchScope branchScope;

    private VehicleService service;

    @BeforeEach
    void setUp() {
        service = new VehicleService(vehicleRepo, customerRepo, auditService, branchScope);
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

    @Test
    void rename_normalisesTheNumber_andKeepsTheOwner_rev77() {
        Vehicle v = vehicle(5L, 42L, "OD05CA4177");
        when(vehicleRepo.findByIdAndOrgId(5L, ORG)).thenReturn(Optional.of(v));
        when(vehicleRepo.findByOrgIdAndVehicleNo(ORG, "OD05CA9999")).thenReturn(Optional.empty());
        when(vehicleRepo.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepo.findByIdAndOrgId(42L, ORG)).thenReturn(Optional.of(customer(42L)));

        VehicleResponse out = service.rename(5L, " od05 ca 9999 ");

        assertThat(out.vehicleNo()).isEqualTo("OD05CA9999");
        assertThat(out.customerId()).isEqualTo(42L);
    }

    @Test
    void rename_toANumberAnotherVehicleHolds_isRefused_rev77() {
        Vehicle mine = vehicle(5L, 42L, "OD05CA4177");
        Vehicle other = vehicle(6L, 43L, "OD05CA9999");
        when(vehicleRepo.findByIdAndOrgId(5L, ORG)).thenReturn(Optional.of(mine));
        when(vehicleRepo.findByOrgIdAndVehicleNo(ORG, "OD05CA9999")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.rename(5L, "OD05 CA 9999"))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("already belongs to another vehicle");
        assertThat(mine.getVehicleNo()).isEqualTo("OD05CA4177");
        verify(vehicleRepo, never()).save(any());
    }

    @Test
    void rename_toItsOwnNumberInADifferentCase_isAllowed_rev77() {
        Vehicle v = vehicle(5L, 42L, "OD05CA4177");
        when(vehicleRepo.findByIdAndOrgId(5L, ORG)).thenReturn(Optional.of(v));
        when(vehicleRepo.findByOrgIdAndVehicleNo(ORG, "OD05CA4177")).thenReturn(Optional.of(v));
        when(vehicleRepo.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepo.findByIdAndOrgId(42L, ORG)).thenReturn(Optional.of(customer(42L)));

        assertThat(service.rename(5L, "od05ca4177").vehicleNo()).isEqualTo("OD05CA4177");
    }

    @Test
    void rename_withNoLettersOrDigits_isRefused_rev77() {
        when(vehicleRepo.findByIdAndOrgId(5L, ORG)).thenReturn(Optional.of(vehicle(5L, 42L, "OD05CA4177")));

        assertThatThrownBy(() -> service.rename(5L, " - - "))
            .isInstanceOf(DamsException.class);
    }

    @Test
    void move_changesOnlyTheOwner_andIsAudited_rev77() {
        Vehicle v = vehicle(5L, 42L, "OD05CA4177");
        Customer to = customer(43L);
        to.setName("New Owner");
        when(vehicleRepo.findByIdAndOrgId(5L, ORG)).thenReturn(Optional.of(v));
        when(customerRepo.findByIdAndOrgId(43L, ORG)).thenReturn(Optional.of(to));
        when(customerRepo.findByIdAndOrgId(42L, ORG)).thenReturn(Optional.of(customer(42L)));
        when(vehicleRepo.save(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));
        when(branchScope.currentUserId()).thenReturn(900L);

        VehicleResponse out = service.move(5L, 43L);

        assertThat(out.customerId()).isEqualTo(43L);
        assertThat(out.customerName()).isEqualTo("New Owner");
        assertThat(out.vehicleNo()).isEqualTo("OD05CA4177");
        ArgumentCaptor<java.util.Map<String, Object>> detail = ArgumentCaptor.forClass(java.util.Map.class);
        verify(auditService).recordUserEvent(eq("Vehicle"), eq(5L), eq(com.dams.audit.entity.EventType.VEHICLE_MOVED),
            eq(900L), detail.capture());
        assertThat(detail.getValue()).containsEntry("fromCustomerId", 42L).containsEntry("toCustomerId", 43L)
            .containsEntry("vehicleNo", "OD05CA4177");
    }

    @Test
    void move_toTheSameCustomer_isANoOp_rev77() {
        Vehicle v = vehicle(5L, 42L, "OD05CA4177");
        Customer same = customer(42L);
        when(vehicleRepo.findByIdAndOrgId(5L, ORG)).thenReturn(Optional.of(v));
        when(customerRepo.findByIdAndOrgId(42L, ORG)).thenReturn(Optional.of(same));

        assertThat(service.move(5L, 42L).customerId()).isEqualTo(42L);
        verify(vehicleRepo, never()).save(any());
        verify(auditService, never()).recordUserEvent(any(), any(), any(com.dams.audit.entity.EventType.class), any(), any());
    }

    @Test
    void move_toAnUnknownCustomer_isRefused_rev77() {
        Vehicle v = vehicle(5L, 42L, "OD05CA4177");
        when(vehicleRepo.findByIdAndOrgId(5L, ORG)).thenReturn(Optional.of(v));
        when(customerRepo.findByIdAndOrgId(99L, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.move(5L, 99L)).isInstanceOf(DamsException.class);
        assertThat(v.getCustomerId()).isEqualTo(42L);
        verify(vehicleRepo, never()).save(any());
    }

    private static Vehicle vehicle(Long id, Long customerId, String no) {
        Vehicle v = new Vehicle();
        ReflectionTestUtils.setField(v, "id", id);
        v.setOrgId(ORG);
        v.setCustomerId(customerId);
        v.setVehicleNo(no);
        return v;
    }

    private static Customer customer(long id) {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", id);
        c.setOrgId(ORG);
        c.setName("Test Customer");
        return c;
    }
}
