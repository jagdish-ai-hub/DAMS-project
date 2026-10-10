package com.dams.customer;

import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.customer.service.PartyResolver;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * rev 70: a typed customer name must never be dropped silently when the vehicle number already
 * belongs to someone else. XYZ owns vehicle OD33AB1234; the cashier typed "BCD".
 */
class PartyResolverVehicleOwnerTest {

    private static final Long ORG = 1L;
    private static final String PLATE = "OD33AB1234";

    private final CustomerRepository customerRepo = mock(CustomerRepository.class);
    private final VehicleRepository vehicleRepo = mock(VehicleRepository.class);
    private final BranchScope branchScope = mock(BranchScope.class);
    private PartyResolver resolver;
    private Customer xyz;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        resolver = new PartyResolver(customerRepo, vehicleRepo, branchScope);
        xyz = new Customer();
        ReflectionTestUtils.setField(xyz, "id", 7L);
        xyz.setOrgId(ORG);
        xyz.setName("XYZ Transport");
        vehicle = new Vehicle();
        ReflectionTestUtils.setField(vehicle, "id", 70L);
        vehicle.setOrgId(ORG);
        vehicle.setCustomerId(7L);
        vehicle.setVehicleNo(PLATE);
        when(customerRepo.findByIdAndOrgId(7L, ORG)).thenReturn(Optional.of(xyz));
        when(vehicleRepo.findByOrgIdAndVehicleNo(ORG, PLATE)).thenReturn(Optional.of(vehicle));
        when(vehicleRepo.findByIdAndOrgId(70L, ORG)).thenReturn(Optional.of(vehicle));
        when(customerRepo.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));
        when(branchScope.allowedBranchIds()).thenReturn(Optional.empty());
    }

    @Test
    void typedNumberOfKnownVehicle_withADifferentTypedName_isRejected_notSilentlyAdopted() {
        assertThatThrownBy(() -> resolver.resolve(ORG, null, "BCD Motors", null, null, "od33 ab1234", 3L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("XYZ Transport")
            .hasMessageContaining("BCD Motors");
    }

    @Test
    void pickedVehicle_withADifferentTypedName_isRejectedToo() {
        assertThatThrownBy(() -> resolver.resolve(ORG, null, "BCD Motors", null, 70L, null, 3L))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("BCD Motors");
    }

    @Test
    void sameNameIgnoringCaseAndSpaces_stillAdoptsTheOwner() {
        var party = resolver.resolve(ORG, null, "  xyz   TRANSPORT ", null, null, PLATE, 3L);
        assertThat(party.customer().getId()).isEqualTo(7L);
        assertThat(party.vehicle().getId()).isEqualTo(70L);
    }

    @Test
    void noTypedName_stillAdoptsTheOwner() {
        var party = resolver.resolve(ORG, null, null, null, null, PLATE, 3L);
        assertThat(party.customer().getId()).isEqualTo(7L);
    }

    @Test
    void ownerResolvedByIdFromTheDialog_goesThroughWithoutAName() {
        var party = resolver.resolve(ORG, 7L, null, null, 70L, null, 3L);
        assertThat(party.customer().getName()).isEqualTo("XYZ Transport");
    }
}
