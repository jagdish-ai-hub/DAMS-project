package com.dams.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V40 (rev 72): files frozen too early — because a receipt merely became fully paid — are opened
 * again, but only on receipts that are not approved/rejected AND were never approved. Re-runs the
 * migration's own SQL against rows seeded here. Needs real Postgres; skipped without Docker, runs in CI.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MigrationV40Test {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbc;

    private long receipt(String no, String status, boolean settled) {
        long jobCard = jdbc.queryForObject("select min(id) from job_card where org_id = 1", Long.class);
        return jdbc.queryForObject("insert into receive_document (org_id, branch_id, job_card_id, document_no, workflow_status,"
            + " settled, created_by) select 1, branch_id, id, ?, ?, ?, 1 from job_card where id = ? returning id",
            Long.class, no, status, settled, jobCard);
    }

    private long file(long receiptId, boolean frozen) {
        return jdbc.queryForObject("insert into attachment (org_id, parent_type, parent_id, r2_object_key, filename,"
            + " content_type, size_bytes, frozen, uploaded_by) values (1, 'RECEIVE_DOCUMENT', ?, 'k', 'f.pdf',"
            + " 'application/pdf', 1, ?, 1) returning id", Long.class, receiptId, frozen);
    }

    private boolean frozen(long attachmentId) {
        return jdbc.queryForObject("select frozen from attachment where id = ?", Boolean.class, attachmentId);
    }

    private void runMigration() throws Exception {
        String sql = StreamUtils.copyToString(
            new ClassPathResource("db/migration/V40__unfreeze_attachments_on_unapproved_receipts.sql").getInputStream(),
            StandardCharsets.UTF_8);
        jdbc.execute(sql);
    }

    @Test
    void opensFilesFrozenOnReceiptsStillInReview_butNotOnesThatWereApproved() throws Exception {
        long queried = file(receipt("T-V40-1", "QUERIED", true), true);      // R-011 / R-012's situation
        long verified = file(receipt("T-V40-2", "VERIFIED", true), true);
        long approved = file(receipt("T-V40-3", "APPROVED", true), true);    // earned its freeze
        long rejected = file(receipt("T-V40-4", "REJECTED", false), true);

        long reopened = receipt("T-V40-5", "SUBMITTED", true);              // approved once, re-opened by an added payment
        // (every receipt here is "settled" except the rejected one: only one OPEN document may exist per job card)
        long reopenedFile = file(reopened, true);
        jdbc.update("insert into audit_event (org_id, entity_type, entity_id, event_type, actor_type) values (1, 'ReceiveDocument', ?, 'APPROVED', 'USER')", reopened);

        runMigration();

        assertThat(frozen(queried)).isFalse();
        assertThat(frozen(verified)).isFalse();
        assertThat(frozen(approved)).isTrue();
        assertThat(frozen(rejected)).isTrue();
        assertThat(frozen(reopenedFile)).isTrue();
    }
}
