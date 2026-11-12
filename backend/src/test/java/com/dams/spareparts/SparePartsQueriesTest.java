package com.dams.spareparts;

import com.dams.customer.entity.Customer;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.organization.entity.Organization;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.spareparts.entity.InvMovement;
import com.dams.spareparts.entity.InvPart;
import com.dams.spareparts.entity.InvStock;
import com.dams.spareparts.entity.InvTransfer;
import com.dams.spareparts.entity.MovementType;
import com.dams.spareparts.entity.TransferStatus;
import com.dams.spareparts.repository.InvJobCardLookupRepository;
import com.dams.spareparts.repository.InvMovementRepository;
import com.dams.spareparts.repository.InvPartRepository;
import com.dams.spareparts.repository.InvStockRepository;
import com.dams.spareparts.repository.InvTransferRepository;
import com.dams.user.entity.AppUser;
import com.dams.vehicle.entity.Vehicle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the module's real database queries on an in-memory H2 in PostgreSQL mode. Booting the repositories already proves every
 * HQL string parses; the tests then check the queries answer correctly. (The real-Postgres check, including that the entities
 * match the V47 migration, is MigrationV47Test, which needs Docker and runs in CI.)
 */
@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:spareparts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SparePartsQueriesTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {InvPart.class, JobCard.class, Customer.class, Vehicle.class, AppUser.class, Organization.class,
        ReceiveDocument.class})
    @EnableJpaRepositories(basePackageClasses = {InvPartRepository.class, JobCardRepository.class})
    static class Cfg {
    }

    private static final long ORG = 1L;
    private static final long OTHER_ORG = 2L;
    private static final long OOJ = 10L;
    private static final long OOB = 11L;

    @Autowired private InvPartRepository parts;
    @Autowired private InvStockRepository stock;
    @Autowired private InvMovementRepository movements;
    @Autowired private InvTransferRepository transfers;
    @Autowired private InvJobCardLookupRepository jobCardLookup;
    @Autowired private JobCardRepository jobCards;

    @Test
    void search_matchesCodeOrDescription_ignoringCase_andLeavesOtherOrganizationsOut() {
        part(ORG, "IA309072", "Fender Assy RH 1114 Sleepar", "3885");
        part(ORG, "MF450155", "PLAIN WASHER", "25");
        part(OTHER_ORG, "IA309072", "Someone else's fender", "1");

        assertThat(parts.search(ORG, "fender", PageRequest.of(0, 10))).extracting(InvPart::getPartCode).containsExactly("IA309072");
        assertThat(parts.search(ORG, "mf45", PageRequest.of(0, 10))).extracting(InvPart::getPartCode).containsExactly("MF450155");
        assertThat(parts.search(ORG, "", PageRequest.of(0, 10))).extracting(InvPart::getPartCode).containsExactly("IA309072", "MF450155");
    }

    @Test
    void findLocked_returnsTheBranchRow_andNothingForABranchThatNeverHeldThePart() {
        InvPart washer = part(ORG, "MF450155", "PLAIN WASHER", "25");
        stockRow(OOB, washer, 6);

        assertThat(stock.findLocked(ORG, OOB, washer.getId())).hasValueSatisfying(s -> assertThat(s.getQuantity()).isEqualTo(6));
        assertThat(stock.findLocked(ORG, OOJ, washer.getId())).isEmpty();
        assertThat(stock.findLocked(OTHER_ORG, OOB, washer.getId())).isEmpty();      // another dealership cannot lock it
    }

    @Test
    void branchTotals_sumUnitsAndValueAtThePartPrice_ignoringEmptyRows() {
        InvPart washer = part(ORG, "MF450155", "PLAIN WASHER", "25");
        InvPart fender = part(ORG, "IA309072", "Fender", "3885");
        stockRow(OOB, washer, 6);
        stockRow(OOB, fender, 1);
        stockRow(OOJ, fender, 0);          // known part, none held: not counted

        List<Object[]> totals = stock.branchTotals(ORG);

        assertThat(totals).hasSize(1);
        Object[] oob = totals.get(0);
        assertThat(oob[0]).isEqualTo(OOB);
        assertThat(((Number) oob[1]).intValue()).isEqualTo(2);                         // parts held
        assertThat(((Number) oob[2]).longValue()).isEqualTo(7);                        // units
        assertThat(new BigDecimal(oob[3].toString())).isEqualByComparingTo("4035");    // 6 x 25 + 1 x 3885
    }

    @Test
    void ledgerSum_andBilledSince_countOnlyWhatTheyShould() {
        InvPart washer = part(ORG, "MF450155", "PLAIN WASHER", "25");
        movement(OOB, washer, MovementType.MANUAL_ADD, 10, 10);
        movement(OOB, washer, MovementType.BILLED, -4, 6);
        movement(OOB, washer, MovementType.BILLED, -1, 5);

        assertThat(movements.sumChange(OOB, washer.getId())).isEqualTo(5);
        List<Object[]> billed = movements.billedSince(ORG, Instant.now().minusSeconds(3600));
        assertThat(billed).hasSize(1);
        assertThat(((Number) billed.get(0)[1]).longValue()).isEqualTo(2);              // two bills
        assertThat(((Number) billed.get(0)[2]).longValue()).isEqualTo(5);              // five units
        assertThat(movements.billedSince(ORG, Instant.now().plusSeconds(3600))).isEmpty();
    }

    @Test
    void transferQueries_countByStatus_andSumWhatIsOnTheRoad() {
        InvPart fender = part(ORG, "IA309072", "Fender", "3885");
        transfer(fender, TransferStatus.REQUESTED, null);
        transfer(fender, TransferStatus.SENT, 2);
        transfer(fender, TransferStatus.SENT, 1);
        transfer(fender, TransferStatus.RECEIVED, 1);

        assertThat(transfers.countByStatus(ORG)).anySatisfy(row -> {
            assertThat(row[0]).isEqualTo(TransferStatus.SENT);
            assertThat(((Number) row[1]).longValue()).isEqualTo(2);
        });
        List<Object[]> onTheRoad = transfers.inTransitByPart(ORG);
        assertThat(onTheRoad).hasSize(1);
        assertThat(((Number) onTheRoad.get(0)[1]).intValue()).isEqualTo(3);            // only the two SENT rows
    }

    @Test
    void claimLookup_countsAJobCardOnlyWhenItIsAClaim_andOnlyInTheSameOrganization() {
        jobCard(ORG, "4010541946", 5L);        // a claim job card (a claim type is set)
        jobCard(ORG, "4010615068", null);      // not a claim
        jobCard(OTHER_ORG, "4010999999", 5L);  // a claim, but another dealership's

        assertThat(jobCardLookup.countClaimJobCards(ORG, "4010541946")).isEqualTo(1);
        assertThat(jobCardLookup.countClaimJobCards(ORG, "4010615068")).isZero();
        assertThat(jobCardLookup.countClaimJobCards(ORG, "4010999999")).isZero();
        assertThat(jobCardLookup.countClaimJobCards(ORG, "does-not-exist")).isZero();
    }

    // --- fixtures ---

    private InvPart part(long org, String code, String description, String price) {
        InvPart p = new InvPart();
        p.setOrgId(org);
        p.setPartCode(code);
        p.setDescription(description);
        p.setUnitPrice(new BigDecimal(price));
        return parts.save(p);
    }

    private void stockRow(long branch, InvPart part, int quantity) {
        // (the production code creates the row with a Postgres-only INSERT ... ON CONFLICT; see MigrationV47Test)
        InvStock s = new InvStock();
        s.setOrgId(ORG);
        org.springframework.test.util.ReflectionTestUtils.setField(s, "branchId", branch);
        org.springframework.test.util.ReflectionTestUtils.setField(s, "partId", part.getId());
        s.setQuantity(quantity);
        stock.save(s);
    }

    private void movement(long branch, InvPart part, MovementType type, int change, int after) {
        InvMovement m = new InvMovement();
        m.setOrgId(ORG);
        m.setBranchId(branch);
        m.setPartId(part.getId());
        m.setMovementType(type);
        m.setQtyChange(change);
        m.setQtyAfter(after);
        movements.save(m);
    }

    private void transfer(InvPart part, TransferStatus status, Integer sent) {
        InvTransfer t = new InvTransfer();
        t.setOrgId(ORG);
        t.setFromBranchId(OOJ);
        t.setToBranchId(OOB);
        t.setPartId(part.getId());
        t.setQtyRequested(2);
        t.setQtySent(sent);
        t.setStatus(status);
        transfers.save(t);
    }

    private void jobCard(long org, String dbm, Long claimTypeId) {
        JobCard j = new JobCard();
        j.setOrgId(org);
        j.setBranchId(OOB);
        j.setDbmId(dbm);
        j.setCategoryId(1L);
        j.setBusinessStatusId(1L);
        j.setClaimTypeId(claimTypeId);
        jobCards.save(j);
    }
}
