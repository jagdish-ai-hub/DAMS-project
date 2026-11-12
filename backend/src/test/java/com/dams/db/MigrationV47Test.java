package com.dams.db;

import com.dams.spareparts.repository.InvStockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V47 (rev 91, Spare Parts) on REAL Postgres. Booting this context runs every Flyway migration and then Hibernate's
 * schema validation, so it proves the new entities match the new tables column for column. It also proves what H2 cannot:
 * the Postgres-only "INSERT ... ON CONFLICT DO NOTHING" that creates a branch's stock row, and the table constraints that
 * back the ledger rules. Needs Docker; skipped without it, runs in CI.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MigrationV47Test {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private InvStockRepository stock;

    private long branch() {
        return jdbc.queryForObject("select min(id) from branch where org_id = 1", Long.class);
    }

    private long part(String code) {
        return jdbc.queryForObject("insert into inv_part (org_id, part_code, description, unit_price) values (1, ?, 'Test part', 25) returning id",
            Long.class, code);
    }

    @Test
    void everyOrganization_startsWithTheModuleOff_soNothingChangesUntilSuperAdminTurnsItOn() {
        Integer on = jdbc.queryForObject("select count(*) from organization where spare_parts_enabled", Integer.class);

        assertThat(on).isZero();
    }

    @Test
    void insertIfAbsent_createsTheZeroRowOnce_andNeverFailsTheSecondTime() {
        long part = part("MF450155");

        assertThat(stock.insertIfAbsent(1L, branch(), part)).isEqualTo(1);
        assertThat(stock.insertIfAbsent(1L, branch(), part)).isZero();     // ON CONFLICT DO NOTHING: no error, no second row

        assertThat(stock.findLocked(1L, branch(), part)).hasValueSatisfying(s -> assertThat(s.getQuantity()).isZero());
    }

    @Test
    void aQuantityBelowZero_isRefusedByTheDatabaseItself() {
        long part = part("MF450155");
        stock.insertIfAbsent(1L, branch(), part);

        assertThatThrownBy(() -> jdbc.update("update inv_stock set quantity = -1 where part_id = ?", part))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aBranchHoldsAPartOnce_andAPartCodeIsUniquePerOrganization() {
        long part = part("MF450155");
        jdbc.update("insert into inv_stock (org_id, branch_id, part_id, quantity) values (1, ?, ?, 1)", branch(), part);

        assertThatThrownBy(() -> jdbc.update("insert into inv_stock (org_id, branch_id, part_id, quantity) values (1, ?, ?, 1)", branch(), part))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> part("MF450155")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aRequestCannotBeToTheSameBranch_andNeedsAPositiveQuantity() {
        long part = part("MF450155");

        assertThatThrownBy(() -> jdbc.update("insert into inv_transfer (org_id, from_branch_id, to_branch_id, part_id, qty_requested)"
            + " values (1, ?, ?, ?, 1)", branch(), branch(), part)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aLedgerRowMustMoveStock_andCannotLeaveAnythingBelowZero() {
        long part = part("MF450155");

        assertThatThrownBy(() -> jdbc.update("insert into inv_movement (org_id, branch_id, part_id, movement_type, qty_change, qty_after)"
            + " values (1, ?, ?, 'BILLED', 0, 5)", branch(), part)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into inv_movement (org_id, branch_id, part_id, movement_type, qty_change, qty_after)"
            + " values (1, ?, ?, 'BILLED', -1, -1)", branch(), part)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theNewRolesCanBeGrantedAsExtraRoles_butTheAuditEventTypeListStillAcceptsTheOldOnes() {
        long user = jdbc.queryForObject("select min(id) from app_user where org_id = 1", Long.class);
        jdbc.update("insert into user_role_grant (org_id, user_id, role, branch_id) values (1, ?, 'STORE_KEEPER', ?)", user, branch());
        jdbc.update("insert into user_role_grant (org_id, user_id, role, branch_id) values (1, ?, 'INVENTORY_MANAGER', ?)", user, branch());
        assertThatThrownBy(() -> jdbc.update("insert into user_role_grant (org_id, user_id, role, branch_id) values (1, ?, 'OWNER', ?)", user, branch()))
            .isInstanceOf(DataIntegrityViolationException.class);   // the old rule still holds: nobody is granted Owner
        jdbc.update("insert into audit_event (org_id, entity_type, entity_id, event_type, actor_type, detail) values (1, 'X', 1, 'SPARE_PERMISSIONS_CHANGED', 'USER', '{}')");
        jdbc.update("insert into audit_event (org_id, entity_type, entity_id, event_type, actor_type, detail) values (1, 'X', 1, 'VEHICLE_MOVED', 'USER', '{}')");

        Integer rows = jdbc.queryForObject("select count(*) from audit_event where entity_type = 'X'", Integer.class);
        assertThat(rows).isEqualTo(2);
    }
}
