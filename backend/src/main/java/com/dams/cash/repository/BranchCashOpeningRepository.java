package com.dams.cash.repository;

import com.dams.cash.entity.BranchCashOpening;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BranchCashOpeningRepository extends JpaRepository<BranchCashOpening, Long> {

    Optional<BranchCashOpening> findByOrgIdAndBranchId(Long orgId, Long branchId);

    /** Every configured one-time opening in the org — the batched form for the drawer roll-up. */
    java.util.List<BranchCashOpening> findByOrgId(Long orgId);

    boolean existsByOrgIdAndBranchId(Long orgId, Long branchId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
