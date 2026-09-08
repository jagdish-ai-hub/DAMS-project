package com.dams.recon.repository;

import com.dams.recon.entity.ReconBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReconBatchRepository extends JpaRepository<ReconBatch, Long> {

    Optional<ReconBatch> findByIdAndOrgId(Long id, Long orgId);

    List<ReconBatch> findByOrgIdOrderByUploadedAtDesc(Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
