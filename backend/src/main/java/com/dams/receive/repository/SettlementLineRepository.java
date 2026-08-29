package com.dams.receive.repository;

import com.dams.receive.entity.SettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SettlementLineRepository extends JpaRepository<SettlementLine, Long> {

    java.util.Optional<SettlementLine> findByIdAndOrgId(Long id, Long orgId);

    java.util.Optional<SettlementLine> findByOrgIdAndReceiveDocumentIdAndLineNo(Long orgId, Long receiveDocumentId, Integer lineNo);

    List<SettlementLine> findByOrgIdAndReceiveDocumentIdOrderByLineNoAsc(Long orgId, Long receiveDocumentId);

    List<SettlementLine> findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(Long orgId, List<Long> receiveDocumentIds);

    /**
     * Sum of every settlement line on a job card, across ALL of its receive documents except
     * REJECTED ones (a rejected document's lines are void). This is the Σ term of Pending
     * Amount — see {@link com.dams.jobcard.service.PendingAmountCalculator}.
     */
    @Query("""
        select coalesce(sum(l.amount), 0)
        from SettlementLine l, ReceiveDocument d
        where l.receiveDocumentId = d.id
          and d.orgId = :orgId
          and d.jobCardId = :jobCardId
          and d.workflowStatus <> com.dams.receive.entity.WorkflowStatus.REJECTED
        """)
    BigDecimal sumAmountForJobCard(@Param("orgId") Long orgId, @Param("jobCardId") Long jobCardId);

    /** Highest line number currently on a document (0 if none) — for the next line_no. */
    @Query("select coalesce(max(l.lineNo), 0) from SettlementLine l where l.receiveDocumentId = :docId")
    int maxLineNo(@Param("docId") Long receiveDocumentId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
