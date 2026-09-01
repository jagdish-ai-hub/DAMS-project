package com.dams.jobcard.repository;

import com.dams.jobcard.entity.ClaimClose;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClaimCloseRepository extends JpaRepository<ClaimClose, Long> {

    Optional<ClaimClose> findByOrgIdAndJobCardId(Long orgId, Long jobCardId);

    boolean existsByOrgIdAndJobCardId(Long orgId, Long jobCardId);

    List<ClaimClose> findByOrgIdAndJobCardIdIn(Long orgId, List<Long> jobCardIds);

    /** Just the closed job-card ids for the org — the batched form of {@link #existsByOrgIdAndJobCardId}. */
    @org.springframework.data.jpa.repository.Query("select c.jobCardId from ClaimClose c where c.orgId = :orgId")
    List<Long> findJobCardIdsByOrgId(@org.springframework.data.repository.query.Param("orgId") Long orgId);

    /** FM queue "recently closed" — newest first. */
    List<ClaimClose> findByOrgIdOrderByClosedAtDesc(Long orgId, Limit limit);

    /** Override Audit — claims closed at a figure other than what was received, in a window. */
    List<ClaimClose> findByOrgIdAndOverriddenTrueAndClosedAtBetweenOrderByClosedAtDesc(
        Long orgId, Instant from, Instant to);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
