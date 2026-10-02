package com.dams.jobcard;

import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.organization.entity.Organization;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.user.entity.AppUser;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the rev-56 picker queries (branch scope inside the query, customerless job cards,
 * typed-only vehicle numbers) against an in-memory H2 in PostgreSQL mode -- the real Postgres
 * run stays in CI; this catches HQL that does not parse or filter as intended.
 */
@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:pickers;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PickerQueriesTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {JobCard.class, Customer.class, Vehicle.class, AppUser.class, Organization.class,
        ReceiveDocument.class})
    @EnableJpaRepositories(basePackageClasses = {JobCardRepository.class, CustomerRepository.class, VehicleRepository.class,
        ReceiveDocumentRepository.class})
    static class Cfg {
    }

    private static final long ORG = 1L;
    private static final long OOR = 10L;
    private static final long OOB = 20L;

    @Autowired private JobCardRepository jobCards;
    @Autowired private CustomerRepository customers;
    @Autowired private VehicleRepository vehicles;
    @Autowired private ReceiveDocumentRepository receipts;

    private Customer ravi;
    private Customer meena;
    private JobCard raviOor;
    private JobCard meenaOob;
    private JobCard customerlessOor;

    @BeforeEach
    void seed() {
        ravi = customer("Ravi Transport", OOR);
        meena = customer("Meena Logistics", OOB);
        Vehicle raviTruck = vehicle(ravi, "OD05CA4177");

        raviOor = jobCard(OOR, ravi.getId(), raviTruck.getId(), null, "4009941587");
        meenaOob = jobCard(OOB, meena.getId(), null, null, null);
        customerlessOor = jobCard(OOR, null, null, "OD02ZZ9999", null);
    }

    private Customer customer(String name, Long createdBranch) {
        Customer c = new Customer();
        c.setOrgId(ORG);
        c.setName(name);
        c.setCreatedBranchId(createdBranch);
        return customers.save(c);
    }

    private Vehicle vehicle(Customer owner, String no) {
        Vehicle v = new Vehicle();
        v.setOrgId(ORG);
        v.setCustomerId(owner.getId());
        v.setVehicleNo(no);
        return vehicles.save(v);
    }

    private JobCard jobCard(Long branch, Long customerId, Long vehicleId, String vehicleText, String dbm) {
        JobCard j = new JobCard();
        j.setOrgId(ORG);
        j.setBranchId(branch);
        j.setCustomerId(customerId);
        j.setVehicleId(vehicleId);
        j.setVehicleNoText(vehicleText);
        j.setDbmId(dbm);
        j.setCategoryId(1L);
        j.setBusinessStatusId(1L);
        return jobCards.save(j);
    }

    private List<JobCard> search(boolean all, Set<Long> branches, Long customerId, String q, String vLike) {
        boolean blank = q.isEmpty();
        return jobCards.searchForPicker(ORG, all, branches, customerId, null, blank,
            "%" + q.toLowerCase() + "%", vLike, null, Limit.of(20));
    }

    @Test
    void branchScopeIsAppliedInsideTheQuery() {
        List<JobCard> oorOnly = search(false, Set.of(OOR), null, "", "#");
        assertThat(oorOnly).extracting(JobCard::getId)
            .containsExactlyInAnyOrder(raviOor.getId(), customerlessOor.getId());

        List<JobCard> everything = search(true, Set.of(-1L), null, "", "#");
        assertThat(everything).hasSize(3);
    }

    @Test
    void matchesCustomerNameVehicleDbmAndTypedOnlyVehicle() {
        assertThat(search(true, Set.of(-1L), null, "ravi", "#")).extracting(JobCard::getId)
            .containsExactly(raviOor.getId());
        assertThat(search(true, Set.of(-1L), null, "od05", "%OD05%")).extracting(JobCard::getId)
            .containsExactly(raviOor.getId());
        assertThat(search(true, Set.of(-1L), null, "4009941", "%4009941%")).extracting(JobCard::getId)
            .containsExactly(raviOor.getId());
        // a customerless job card is found by the vehicle number that is only kept as text
        assertThat(search(true, Set.of(-1L), null, "zz9999", "%ZZ9999%")).extracting(JobCard::getId)
            .containsExactly(customerlessOor.getId());
    }

    private ReceiveDocument receipt(JobCard jc, String documentNo) {
        ReceiveDocument r = new ReceiveDocument();
        r.setOrgId(ORG);
        r.setBranchId(jc.getBranchId());
        r.setJobCardId(jc.getId());
        r.setDocumentNo(documentNo);
        r.setCreatedBy(1L);
        return receipts.save(r);
    }

    @Test
    void matchesTheDamsReceiveIdOfAnyReceiptOnTheJobCard() {
        receipt(raviOor, "OOR-AUG26-R-001");
        receipt(meenaOob, "OOB-AUG26-R-007");
        receipt(customerlessOor, null);   // an unnumbered draft receipt has no Ooriba ID yet

        assertThat(search(true, Set.of(-1L), null, "oor-aug26-r-001", "#")).extracting(JobCard::getId)
            .containsExactly(raviOor.getId());
        assertThat(search(true, Set.of(-1L), null, "r-007", "#")).extracting(JobCard::getId)
            .containsExactly(meenaOob.getId());
        // branch scope still wins: OOR staff cannot find the OOB receipt by its number
        assertThat(search(false, Set.of(OOR), null, "OOB-AUG26-R-007", "#")).isEmpty();
    }

    @Test
    void receiveNumbersListNumberedReceiptsNewestFirst() {
        receipt(raviOor, "OOR-AUG26-R-001");
        receipt(raviOor, null);
        receipt(raviOor, "OOR-AUG26-R-002");

        List<Object[]> rows = jobCards.receiveNumbersFor(ORG, List.of(raviOor.getId(), meenaOob.getId()));
        assertThat(rows).extracting(r -> r[1]).containsExactly("OOR-AUG26-R-002", "OOR-AUG26-R-001");
        assertThat(rows).allSatisfy(r -> assertThat(r[0]).isEqualTo(raviOor.getId()));
    }

    @Test
    void customerFilterNarrowsToThatCustomer() {
        assertThat(search(true, Set.of(-1L), meena.getId(), "", "#")).extracting(JobCard::getId)
            .containsExactly(meenaOob.getId());
    }

    @Test
    void customerSearchIsScopedToCreatedOrJobCardBranch() {
        Customer walkIn = customer("Walk-in Fleet", OOR); // no job card yet, created at OOR
        assertThat(customers.searchInBranches(ORG, "", true, Set.of(OOR), Limit.of(20)))
            .extracting(Customer::getId)
            .containsExactlyInAnyOrder(ravi.getId(), walkIn.getId());
        assertThat(customers.searchInBranches(ORG, "meena", false, Set.of(OOR), Limit.of(20))).isEmpty();
        assertThat(customers.searchInBranches(ORG, "meena", false, Set.of(OOB), Limit.of(20)))
            .extracting(Customer::getId).containsExactly(meena.getId());
    }
}
