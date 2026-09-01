package com.dams.audit.repository;

import com.dams.audit.entity.AuditEvent;
import com.dams.audit.entity.EventType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
        Long orgId, String entityType, Long entityId);

    /**
     * Override Audit — events of one type in a time window, optionally narrowed to a branch
     * or an actor (pass null to skip that filter). Newest first.
     */
    @Query("""
        select e from AuditEvent e
        where e.orgId = :orgId
          and e.eventType = :eventType
          and e.createdAt >= :from and e.createdAt < :to
          and (:branchId is null or e.branchId = :branchId)
          and (:actorId is null or e.actorId = :actorId)
        order by e.createdAt desc
        """)
    List<AuditEvent> findForOverrideAudit(@Param("orgId") Long orgId,
                                          @Param("eventType") EventType eventType,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to,
                                          @Param("branchId") Long branchId,
                                          @Param("actorId") Long actorId);

    /**
     * Owner dashboard activity feed — the most recent events of the given types, optionally
     * one branch. Pass a {@code Pageable} with the page size as the limit.
     */
    @Query("""
        select e from AuditEvent e
        where e.orgId = :orgId
          and (:branchId is null or e.branchId = :branchId)
          and e.eventType in :types
        order by e.createdAt desc
        """)
    List<AuditEvent> findRecentActivity(@Param("orgId") Long orgId,
                                        @Param("branchId") Long branchId,
                                        @Param("types") Collection<EventType> types,
                                        Pageable pageable);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
