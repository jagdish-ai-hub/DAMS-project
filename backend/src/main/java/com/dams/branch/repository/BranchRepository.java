package com.dams.branch.repository;

import com.dams.branch.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findByOrgIdOrderByCodeAsc(Long orgId);

    /** Single-row read scoped by org — never bare findById for a tenant entity. */
    Optional<Branch> findByIdAndOrgId(Long id, Long orgId);

    Optional<Branch> findByOrgIdAndCodeIgnoreCase(Long orgId, String code);

    boolean existsByOrgIdAndCodeIgnoreCase(Long orgId, String code);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
