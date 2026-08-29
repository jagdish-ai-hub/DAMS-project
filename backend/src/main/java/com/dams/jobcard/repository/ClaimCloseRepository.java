package com.dams.jobcard.repository;

import com.dams.jobcard.entity.ClaimClose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimCloseRepository extends JpaRepository<ClaimClose, Long> {

    Optional<ClaimClose> findByOrgIdAndJobCardId(Long orgId, Long jobCardId);

    boolean existsByOrgIdAndJobCardId(Long orgId, Long jobCardId);

    List<ClaimClose> findByOrgIdAndJobCardIdIn(Long orgId, List<Long> jobCardIds);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
