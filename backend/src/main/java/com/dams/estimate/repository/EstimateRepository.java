package com.dams.estimate.repository;

import com.dams.estimate.entity.Estimate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EstimateRepository extends JpaRepository<Estimate, Long> {

    Optional<Estimate> findByIdAndOrgId(Long id, Long orgId);

    List<Estimate> findByOrgIdAndJobCardIdOrderByCreatedAtDesc(Long orgId, Long jobCardId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
