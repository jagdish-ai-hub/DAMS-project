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

    @Test
    void awaitingApprovalAndActivityQueries_runAgainstTheRealSchema() {
        var s = dashboard.summary(null, "mtd");
        assertThat(s.kpis().collectionsAwaiting()).isNotNull();
        assertThat(s.kpis().expensesAwaiting()).isNotNull();
        assertThat(dashboard.activity(null, 20)).isNotNull();
    }
}
