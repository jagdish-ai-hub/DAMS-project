package com.dams.dashboard;

import com.dams.cash.service.DrawerService;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.dashboard.dto.MoneyMovementItem;
import com.dams.dashboard.dto.PendingWork;
import com.dams.dashboard.service.DashboardService;
import com.dams.dashboard.service.PendingWorkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * rev 71 — the Owner dashboard's new queries against a real Postgres with every migration applied
 * (the demo org's branches OOJ=1, OOB=2, OOR=3). Proves the JPQL parses and runs, that cash in hand
 * counts days that were never closed, that its breakdown always sums to the card, and that every
 * entry on the "stuck with whom" card is held by exactly one person. Requires Docker (CI).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class DashboardAccuracyDbTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final long ORG = 1L;
    private static final long BRANCH = 1L;     // OOJ — no seeded cash movement

    @Autowired private DashboardService dashboard;
    @Autowired private PendingWorkService pending;
    @Autowired private DrawerService drawer;
    @Autowired private JdbcTemplate jdbc;

    private final LocalDate today = OrgTime.today();

    @BeforeEach
    void tenant() {
        TenantContext.setOrgId(ORG);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private void close(LocalDate date, String counted) {
        jdbc.update("insert into cash_day_close (org_id, branch_id, close_date, opening_amount, computed_closing,"
            + " counted_amount, variance, closed_by) values (?,?,?,?,?,?,0,1)",
            ORG, BRANCH, date, new BigDecimal(counted), new BigDecimal(counted), new BigDecimal(counted));
    }

    private void cashDoc(String direction, LocalDate date, String amount, String status) {
        jdbc.update("insert into cash_document (org_id, branch_id, direction, transaction_date, amount,"
            + " workflow_status, created_by) values (?,?,?,?,?,?,1)", ORG, BRANCH, direction, date, new BigDecimal(amount), status);
    }

    @Test
    void cashInHand_countsTheDaysThatWereNeverClosed_andTheBreakdownSumsToIt() {
        close(today.minusDays(10), "1000");          // last count, ten days ago — never closed since
        cashDoc("IN", today.minusDays(5), "500", "APPROVED");
        cashDoc("OUT", today.minusDays(3), "100", "APPROVED");
        cashDoc("IN", today, "200", "SUBMITTED");
        cashDoc("IN", today.minusDays(2), "9999", "DRAFT");      // a draft is not money yet
        cashDoc("OUT", today.minusDays(1), "777", "REJECTED");   // a rejected one never moved
        cashDoc("IN", today.minusDays(20), "5555", "APPROVED");  // before the last count — already inside it

        BigDecimal running = drawer.runningPositions(ORG, List.of(BRANCH), today).get(BRANCH).position();
        assertThat(running).isEqualByComparingTo("1600");        // 1000 + 500 − 100 + 200

        // the Cash page's one-day formula is unchanged: last count + today only
        assertThat(drawer.position(ORG, BRANCH, today).computedPosition()).isEqualByComparingTo("1200");

        List<MoneyMovementItem> rows = dashboard.cashBreakdown(BRANCH);
        assertThat(rows.stream().map(MoneyMovementItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo(running);
        assertThat(rows).extracting(MoneyMovementItem::kind).contains("opening", "cash-in", "cash-out");
        assertThat(rows).filteredOn(r -> r.kind().equals("cash-out"))
            .allSatisfy(r -> assertThat(r.amount()).isNegative());
    }

    @Test
    void theCardAndItsBreakdown_agreeForEveryBranch() {
        var kpis = dashboard.summary(null, "mtd").kpis();
        BigDecimal breakdown = dashboard.cashBreakdown(null).stream()
            .map(MoneyMovementItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(breakdown).isEqualByComparingTo(kpis.cashInHand());
    }

    @Test
    void everyPendingEntry_isHeldByExactlyOnePerson() {
        PendingWork work = pending.summary(null);
        assertThat(work.groups()).extracting(PendingWork.Group::holder)
            .containsExactly("CASHIER", "ACCOUNTANT", "FINANCE_MANAGER");
        for (PendingWork.Group g : work.groups()) {
            assertThat(g.count() + g.draftCount()).isEqualTo(g.items().size());
            assertThat(g.items().stream().filter(i -> !i.draft()).count()).isEqualTo(g.count());
        }
        long distinct = work.groups().stream().flatMap(g -> g.items().stream())
            .map(i -> i.type() + ":" + i.id()).distinct().count();
        assertThat(distinct).isEqualTo(work.groups().stream().mapToLong(g -> g.items().size()).sum());
        // nothing finished is ever "stuck"
        assertThat(work.groups().stream().flatMap(g -> g.items().stream()).map(PendingWork.Item::workflowStatus))
            .doesNotContain("REJECTED", "CLOSED");
    }

    /** rev 73 — a closed claim counts at the FM's final amount; the drill-down rows add up to the card. */
    @Test
    void closedClaim_countsAtItsFinalAmount_andTheDrillDownSumsToTheCard() {
        // a seeded APPROVED receipt, closed as a claim ₹100 below what its approved lines add up to
        long jobCardId = jdbc.queryForObject("select job_card_id from receive_document where workflow_status = 'APPROVED' order by id limit 1", Long.class);
        BigDecimal lines = jdbc.queryForObject("select coalesce(sum(l.amount),0) from settlement_line l join receive_document d on d.id = l.receive_document_id"
            + " where d.job_card_id = ? and d.workflow_status = 'APPROVED'", BigDecimal.class, jobCardId);
        jdbc.update("insert into claim_close (org_id, job_card_id, final_amount, overridden, closed_by, closed_at) values (1, ?, ?, false, 1, now())",
            jobCardId, lines.subtract(new BigDecimal("100")));

        var kpis = dashboard.summary(null, "today").kpis();
        BigDecimal rows = dashboard.collectionsBreakdown(null, "today").stream()
            .map(MoneyMovementItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(rows).isEqualByComparingTo(kpis.collections());
        assertThat(dashboard.collectionsBreakdown(null, "today")).filteredOn(r -> r.kind().equals("claim-adjustment"))
            .singleElement().satisfies(r -> assertThat(r.amount()).isEqualByComparingTo("-100"));
        assertThat(dashboard.summary(null, "today").byMode())
            .anyMatch(m -> m.name().equals(DashboardService.CLAIM_ADJUSTMENT_LABEL) && m.amount().compareTo(new BigDecimal("-100")) == 0);
    }

    /** rev 73 — Outstanding drops a job card whose only receipt is a blank draft, and nothing else. */
    @Test
    void outstanding_dropsABlankDraftOnly() {
        int before = dashboard.outstanding(null).size();
        // a copy of an existing job card, owing ₹5,200, with a draft receipt carrying no lines
        long copy = jdbc.queryForObject("insert into job_card (org_id, branch_id, customer_id, vehicle_id, dbm_id, invoice_no, invoice_amount,"
            + " category_id, business_status_id, is_b2b) select org_id, branch_id, customer_id, vehicle_id, 'T-D-1', 'T-INV-1', 5200,"
            + " category_id, business_status_id, false from job_card where org_id = 1 order by id limit 1 returning id", Long.class);
        jdbc.update("insert into receive_document (org_id, branch_id, job_card_id, workflow_status, settled, created_by)"
            + " select org_id, branch_id, id, 'DRAFT', false, 1 from job_card where id = ?", copy);

        assertThat(dashboard.outstanding(null)).hasSize(before);          // the blank draft is not listed

        // once a payment line exists on that draft it IS a part-paid job again
        jdbc.update("insert into settlement_line (org_id, receive_document_id, line_no, line_id, transaction_date, settlement_mode_id, amount, created_by)"
            + " select 1, d.id, 1, 'T-L1', current_date, (select min(id) from settlement_mode where org_id = 1), 1000, 1 from receive_document d where d.job_card_id = ?", copy);
        assertThat(dashboard.outstanding(null)).hasSize(before + 1);
    }

    @Test
    void awaitingApprovalAndActivityQueries_runAgainstTheRealSchema() {
        var s = dashboard.summary(null, "mtd");
        assertThat(s.kpis().collectionsAwaiting()).isNotNull();
        assertThat(s.kpis().expensesAwaiting()).isNotNull();
        assertThat(dashboard.activity(null, 20)).isNotNull();
    }
}
