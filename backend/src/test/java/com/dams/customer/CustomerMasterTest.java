package com.dams.customer;

import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.dto.CustomerRequest;
import com.dams.customer.dto.CustomerResponse;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.customer.service.CustomerService;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.branch.repository.BranchRepository;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receive.service.ReceivePaymentGuard;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decision #2: a branch-restricted caller sees only their branches' job cards in a
 * customer's history — identity (name, phone, vehicles) stays visible, everything
 * derived from job cards (cards, timeline, totals) is scoped.
 */
@ExtendWith(MockitoExtension.class)
class CustomerMasterTest {

    private static final Long ORG = 1L;

    @Mock private CustomerRepository customerRepo;
    @Mock private VehicleRepository vehicleRepo;
    @Mock private JobCardRepository jobCardRepo;
    @Mock private BranchRepository branchRepo;
    @Mock private ReceiveCategoryRepository categoryRepo;
    @Mock private ReceiveBusinessStatusRepository statusRepo;
    @Mock private ReceiveDocumentRepository receiveDocumentRepo;
    @Mock private SettlementLineRepository settlementLineRepo;
    @Mock private SettlementModeRepository settlementModeRepo;
    @Mock private PendingAmountCalculator pendingAmountCalculator;
    @Mock private ReceivePaymentGuard paymentGuard;
    @Mock private BranchScope branchScope;
    @Mock private ExpenseDocumentRepository expenseDocumentRepo;
    @Mock private ExpenseLineRepository expenseLineRepo;
    @Mock private ExpenseCategoryRepository expenseCategoryRepo;
    @Mock private ReceiverRepository receiverRepo;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepo, vehicleRepo, jobCardRepo, branchRepo,
            categoryRepo, statusRepo, receiveDocumentRepo, settlementLineRepo, settlementModeRepo,
            pendingAmountCalculator, paymentGuard, branchScope,
            expenseDocumentRepo, expenseLineRepo, expenseCategoryRepo, receiverRepo);
        TenantContext.setOrgId(ORG);
        lenient().when(customerRepo.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_storesTheAddress_trimmed_orNullWhenBlank() {
        CustomerRequest withAddress = request("Sumati Sahoo", "98765 43210", "  12 Station Road, Rayagada  ");
        assertThat(service.create(withAddress).address()).isEqualTo("12 Station Road, Rayagada");

        assertThat(service.create(request("No Address", null, "   ")).address()).isNull();
    }

    @Test
    void update_withNoAddress_neverErasesTheSavedOne_aCashiersNameAndPhoneEdit() {
        Customer c = customer(7L, "Old Name", "111", "12 Station Road");
        when(customerRepo.findByIdAndOrgId(7L, ORG)).thenReturn(Optional.of(c));

        CustomerResponse res = service.update(7L, request("New Name", "222", null));

        assertThat(res.name()).isEqualTo("New Name");
        assertThat(res.phone()).isEqualTo("222");
        assertThat(res.address()).isEqualTo("12 Station Road");
    }

    @Test
    void update_withAnAddress_changesIt_andAnEmptyOneClearsIt() {
        Customer c = customer(7L, "Name", "111", "Old address");
        when(customerRepo.findByIdAndOrgId(7L, ORG)).thenReturn(Optional.of(c));

        assertThat(service.update(7L, request("Name", "111", "New address")).address()).isEqualTo("New address");
        assertThat(service.update(7L, request("Name", "111", "")).address()).isNull();
    }

    @Test
    void create_storesTheGstNo_trimmedAndUpperCased_orNullWhenBlank_rev84() {
        CustomerRequest r = request("Kalaivani Logistics", "94370 11111", null);
        r.setGstNo("  21abcde1234f1z5 ");
        assertThat(service.create(r).gstNo()).isEqualTo("21ABCDE1234F1Z5");

        CustomerRequest blank = request("No Gst", null, null);
        blank.setGstNo("   ");
        assertThat(service.create(blank).gstNo()).isNull();
    }

    @Test
    void update_withNoGstNo_neverErasesTheSavedOne_andAnEmptyOneClearsIt_rev84() {
        Customer c = customer(7L, "Name", "111", null);
        c.setGstNo("21ABCDE1234F1Z5");
        when(customerRepo.findByIdAndOrgId(7L, ORG)).thenReturn(Optional.of(c));

        // a cashier's name / phone edit sends no gstNo
        assertThat(service.update(7L, request("New Name", "222", null)).gstNo()).isEqualTo("21ABCDE1234F1Z5");

        CustomerRequest change = request("New Name", "222", null);
        change.setGstNo("27aaapl1234c1zv");
        assertThat(service.update(7L, change).gstNo()).isEqualTo("27AAAPL1234C1ZV");

        CustomerRequest clear = request("New Name", "222", null);
        clear.setGstNo("");
        assertThat(service.update(7L, clear).gstNo()).isNull();
    }

    @Test
    void listAll_isTheWholeOrganisation_withVehicles_notBranchScoped_notCapped() {
        List<Customer> many = new ArrayList<>();
        for (long i = 1; i <= 45; i++) {
            many.add(customer(i, "Customer " + i, null, null));
        }
        when(customerRepo.findByOrgIdOrderByNameAsc(ORG)).thenReturn(many);
        Vehicle v = new Vehicle();
        ReflectionTestUtils.setField(v, "id", 900L);
        v.setOrgId(ORG);
        v.setCustomerId(3L);
        v.setVehicleNo("OD05CA4177");
        when(vehicleRepo.findByOrgIdAndCustomerIdInOrderByVehicleNoAsc(eq(ORG), any())).thenReturn(List.of(v));

        List<CustomerResponse> out = service.listAll();

        assertThat(out).hasSize(45); // the picker caps at 20; the Owner's master does not
        assertThat(out.get(2).vehicles()).extracting(CustomerResponse.VehicleRef::vehicleNo).containsExactly("OD05CA4177");
        verify(branchScope, never()).allowedBranchIds();
    }

    private static CustomerRequest request(String name, String phone, String address) {
        CustomerRequest r = new CustomerRequest();
        r.setName(name);
        r.setPhone(phone);
        r.setAddress(address);
        return r;
    }

    private static Customer customer(Long id, String name, String phone, String address) {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", id);
        c.setOrgId(ORG);
        c.setName(name);
        c.setPhone(phone);
        c.setAddress(address);
        return c;
    }
}
