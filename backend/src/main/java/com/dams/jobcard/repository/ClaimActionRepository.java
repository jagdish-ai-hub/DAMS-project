package com.dams.jobcard.repository;

import com.dams.jobcard.entity.ClaimAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClaimActionRepository extends JpaRepository<ClaimAction, Long> {

    Optional<ClaimAction> findByIdAndOrgId(Long id, Long orgId);

    List<ClaimAction> findByOrgIdAndJobCardIdOrderByDueDateAsc(Long orgId, Long jobCardId);

    List<ClaimAction> findByOrgIdAndDoneAtIsNullOrderByDueDateAsc(Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
