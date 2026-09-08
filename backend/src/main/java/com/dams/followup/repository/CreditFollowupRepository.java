package com.dams.followup.repository;

import com.dams.followup.entity.CreditFollowup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CreditFollowupRepository extends JpaRepository<CreditFollowup, Long> {

    Optional<CreditFollowup> findByIdAndOrgId(Long id, Long orgId);

    List<CreditFollowup> findByOrgIdAndStatusInOrderByDueDateAsc(Long orgId, List<String> statuses);

    Optional<CreditFollowup> findByOrgIdAndReceiveDocumentIdAndStatusIn(
        Long orgId, Long receiveDocumentId, List<String> statuses);

    List<CreditFollowup> findByOrgIdAndReceiveDocumentId(Long orgId, Long receiveDocumentId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
