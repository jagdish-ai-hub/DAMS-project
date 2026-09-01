package com.dams.receive.repository;

import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.WorkflowStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /** Accountant review queue — documents in one workflow state within the caller's branches, oldest first. */
    List<ReceiveDocument> findByOrgIdAndWorkflowStatusAndBranchIdInOrderBySubmittedAtAscIdAsc(
        Long orgId, WorkflowStatus workflowStatus, Collection<Long> branchIds);

    /** Finance Manager queue — documents in one workflow state org-wide, oldest first. */
    List<ReceiveDocument> findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(
        Long orgId, WorkflowStatus workflowStatus);

    boolean existsByOrgIdAndDocumentNo(Long orgId, String documentNo);

    /** Owner dashboard — receipts still in the review pipeline (SUBMITTED / VERIFIED / QUERIED). */
    @Query("""
        select count(d) from ReceiveDocument d
        where d.orgId = :orgId
          and (:branchId is null or d.branchId = :branchId)
          and d.workflowStatus in (com.dams.receive.entity.WorkflowStatus.SUBMITTED,
                                   com.dams.receive.entity.WorkflowStatus.VERIFIED,
                                   com.dams.receive.entity.WorkflowStatus.QUERIED)
        """)
    long countPendingReview(@Param("orgId") Long orgId, @Param("branchId") Long branchId);

    /** {@code [branchId, count]} of pipeline receipts per branch — one query for the whole dashboard. */
    @Query("""
        select d.branchId, count(d) from ReceiveDocument d
        where d.orgId = :orgId
          and d.workflowStatus in (com.dams.receive.entity.WorkflowStatus.SUBMITTED,
                                   com.dams.receive.entity.WorkflowStatus.VERIFIED,
                                   com.dams.receive.entity.WorkflowStatus.QUERIED)
        group by d.branchId
        """)
    List<Object[]> countPendingReviewByBranch(@Param("orgId") Long orgId);

    /** Universal search — a document-number fragment resolves to its job card / customer. */
    List<ReceiveDocument> findByOrgIdAndDocumentNoContainingIgnoreCase(Long orgId, String fragment);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
