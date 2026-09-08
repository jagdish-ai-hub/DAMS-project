package com.dams.recon.repository;

import com.dams.recon.entity.ReconLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReconLineRepository extends JpaRepository<ReconLine, Long> {

    Optional<ReconLine> findByIdAndOrgId(Long id, Long orgId);

    List<ReconLine> findByOrgIdAndBatchIdOrderByTxnDateAsc(Long orgId, Long batchId);

    /** Super Admin org-purge only (batches cascade first). */
    long deleteByOrgId(Long orgId);
}
