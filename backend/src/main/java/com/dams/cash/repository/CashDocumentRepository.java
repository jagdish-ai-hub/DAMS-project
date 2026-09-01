package com.dams.cash.repository;

import com.dams.cash.entity.CashDirection;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.entity.CashWorkflowStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CashDocumentRepository extends JpaRepository<CashDocument, Long> {

    Optional<CashDocument> findByIdAndOrgId(Long id, Long orgId);

    /** The branch's cash movements for one day, oldest first (the Cash page movements table). */
    List<CashDocument> findByOrgIdAndBranchIdAndTransactionDateOrderByIdAsc(Long orgId, Long branchId, LocalDate transactionDate);

    /** My Entries — the caller's own cash documents, newest first. */
    List<CashDocument> findByOrgIdAndCreatedByOrderByCreatedAtDesc(Long orgId, Long createdBy, Limit limit);

    /** Accountant review queue — cash movements in one workflow state within the caller's branches, oldest first. */
    List<CashDocument> findByOrgIdAndWorkflowStatusAndBranchIdInOrderBySubmittedAtAscIdAsc(
        Long orgId, CashWorkflowStatus workflowStatus, Collection<Long> branchIds);

    /** Finance Manager queue — cash movements in one workflow state org-wide, oldest first. */
    List<CashDocument> findByOrgIdAndWorkflowStatusOrderBySubmittedAtAscIdAsc(
        Long orgId, CashWorkflowStatus workflowStatus);

    boolean existsByOrgIdAndDocumentNo(Long orgId, String documentNo);

    /** Owner dashboard — cash movements still in the review pipeline (SUBMITTED / VERIFIED / QUERIED). */
    @Query("""
        select count(c) from CashDocument c
        where c.orgId = :orgId
          and (:branchId is null or c.branchId = :branchId)
          and c.workflowStatus in (com.dams.cash.entity.CashWorkflowStatus.SUBMITTED,
                                   com.dams.cash.entity.CashWorkflowStatus.VERIFIED,
                                   com.dams.cash.entity.CashWorkflowStatus.QUERIED)
        """)
    long countPendingReview(@Param("orgId") Long orgId, @Param("branchId") Long branchId);

    /** {@code [branchId, count]} of pipeline cash movements per branch — one query for the whole dashboard. */
    @Query("""
        select c.branchId, count(c) from CashDocument c
        where c.orgId = :orgId
          and c.workflowStatus in (com.dams.cash.entity.CashWorkflowStatus.SUBMITTED,
                                   com.dams.cash.entity.CashWorkflowStatus.VERIFIED,
                                   com.dams.cash.entity.CashWorkflowStatus.QUERIED)
        group by c.branchId
        """)
    List<Object[]> countPendingReviewByBranch(@Param("orgId") Long orgId);

    /**
     * {@code [branchId, Σ amount]} for one direction on one date across the whole org — the
     * batched, group-by-branch form of {@link #sumForBranchDateAndDirection} used by the
     * drawer roll-up on the Owner dashboard.
     */
    @Query("""
        select c.branchId, coalesce(sum(c.amount), 0)
        from CashDocument c
        where c.orgId = :orgId
          and c.transactionDate = :date
          and c.direction = :direction
          and c.workflowStatus <> com.dams.cash.entity.CashWorkflowStatus.DRAFT
          and c.workflowStatus <> com.dams.cash.entity.CashWorkflowStatus.REJECTED
        group by c.branchId
        """)
    List<Object[]> sumByBranchForDateAndDirection(@Param("orgId") Long orgId,
                                                  @Param("date") LocalDate date,
                                                  @Param("direction") CashDirection direction);

    /**
     * Total cash moved in a direction for a branch on a date, counting every non-DRAFT,
     * non-REJECTED movement — see {@code DrawerService} for why review state below APPROVED
     * still counts.
     */
    @Query("""
        select coalesce(sum(c.amount), 0)
        from CashDocument c
        where c.orgId = :orgId
          and c.branchId = :branchId
          and c.transactionDate = :date
          and c.direction = :direction
          and c.workflowStatus <> com.dams.cash.entity.CashWorkflowStatus.DRAFT
          and c.workflowStatus <> com.dams.cash.entity.CashWorkflowStatus.REJECTED
        """)
    BigDecimal sumForBranchDateAndDirection(@Param("orgId") Long orgId,
                                            @Param("branchId") Long branchId,
                                            @Param("date") LocalDate date,
                                            @Param("direction") CashDirection direction);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
