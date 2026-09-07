package com.dams.cash.repository;

import com.dams.cash.entity.CashCloseReopenRequest;
import com.dams.cash.entity.ReopenRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CashCloseReopenRequestRepository extends JpaRepository<CashCloseReopenRequest, Long> {

    Optional<CashCloseReopenRequest> findByIdAndOrgId(Long id, Long orgId);

    List<CashCloseReopenRequest> findByOrgIdAndBranchIdOrderByCreatedAtDesc(Long orgId, Long branchId);

    List<CashCloseReopenRequest> findByOrgIdOrderByCreatedAtDesc(Long orgId);

    List<CashCloseReopenRequest> findByOrgIdAndStatusOrderByCreatedAtDesc(Long orgId, ReopenRequestStatus status);

    boolean existsByOrgIdAndBranchIdAndCloseDateAndStatus(
        Long orgId, Long branchId, LocalDate closeDate, ReopenRequestStatus status);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
