package com.dams.receive.repository;

import com.dams.receive.entity.ReceiveDocument;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiveDocumentRepository extends JpaRepository<ReceiveDocument, Long> {

    Optional<ReceiveDocument> findByIdAndOrgId(Long id, Long orgId);

    /** The at-most-one open document for a job card — the target of "Add Payment". */
    Optional<ReceiveDocument> findByOrgIdAndJobCardIdAndSettledFalse(Long orgId, Long jobCardId);

    /** Every document on a job card, newest first (history, roll-ups). */
    List<ReceiveDocument> findByOrgIdAndJobCardIdOrderByCreatedAtDesc(Long orgId, Long jobCardId);

    List<ReceiveDocument> findByOrgIdAndJobCardIdInOrderByCreatedAtDesc(Long orgId, List<Long> jobCardIds);

    /** My Entries — the caller's own documents, newest first. */
    List<ReceiveDocument> findByOrgIdAndCreatedByOrderByCreatedAtDesc(Long orgId, Long createdBy, Limit limit);

    boolean existsByOrgIdAndDocumentNo(Long orgId, String documentNo);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
