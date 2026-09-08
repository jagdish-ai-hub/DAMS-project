package com.dams.estimate.repository;

import com.dams.estimate.entity.EstimateLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstimateLineRepository extends JpaRepository<EstimateLine, Long> {

    List<EstimateLine> findByOrgIdAndEstimateIdOrderByLineNoAsc(Long orgId, Long estimateId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
