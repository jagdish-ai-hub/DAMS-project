package com.dams.branch.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.entity.DocType;
import com.dams.common.time.OrgTime;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Gap-free, sequential, never-reused document numbers: {@code OOR-AUG26-R-001}.
 *
 * The counter row in {@code document_sequence} is advanced with a single
 * {@code INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING} — atomic, so concurrent submits
 * serialise on the row and never collide or skip. It MUST run inside the caller's submit
 * transaction: if that transaction rolls back, the increment rolls back with it, so an
 * abandoned submit burns no number. This is the one place locking is deliberate — see
 * AGENT.md / plan.md "DocumentSequence".
 */
@Service
public class DocumentNumberService {

    private static final Logger log = LoggerFactory.getLogger(DocumentNumberService.class);

    /** e.g. "AUG26" — English month abbreviation + 2-digit year, upper-cased. */
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("MMMyy", Locale.ENGLISH);

    private final EntityManager entityManager;

    public DocumentNumberService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public static String currentMonthKey() {
        return OrgTime.today().format(MONTH_KEY).toUpperCase(Locale.ENGLISH);
    }

    /**
     * Reserve and return the next number for this branch / month / type. Call once, inside
     * the submit transaction, at the moment a document leaves DRAFT.
     */
    @Transactional
    public String nextNumber(Long orgId, Branch branch, DocType docType) {
        String monthKey = currentMonthKey();
        int seq = advanceCounter(orgId, branch.getId(), monthKey, docType.name());
        String number = "%s-%s-%s-%03d".formatted(branch.getCode(), monthKey, docType.name(), seq);
        log.info("Document number assigned: orgId={} branchId={} {}", orgId, branch.getId(), number);
        return number;
    }

    private int advanceCounter(Long orgId, Long branchId, String monthKey, String docType) {
        // ON CONFLICT DO UPDATE ... RETURNING gives us the new value atomically. Postgres locks
        // the conflicting row for the duration of the statement, so racing submits queue up.
        Object result = entityManager.createNativeQuery("""
                INSERT INTO document_sequence (org_id, branch_id, month_key, doc_type, last_seq)
                VALUES (:orgId, :branchId, :monthKey, :docType, 1)
                ON CONFLICT (org_id, branch_id, month_key, doc_type)
                DO UPDATE SET last_seq = document_sequence.last_seq + 1
                RETURNING last_seq
                """)
            .setParameter("orgId", orgId)
            .setParameter("branchId", branchId)
            .setParameter("monthKey", monthKey)
            .setParameter("docType", docType)
            .getSingleResult();
        return ((Number) result).intValue();
    }
}
