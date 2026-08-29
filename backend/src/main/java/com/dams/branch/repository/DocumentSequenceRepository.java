package com.dams.branch.repository;

import com.dams.branch.entity.DocType;
import com.dams.branch.entity.DocumentSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Created in Stage 2, exercised in Stage 4. The locking read for gap-free numbering
 * (SELECT ... FOR UPDATE) is added there.
 */
public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    Optional<DocumentSequence> findByOrgIdAndBranchIdAndMonthKeyAndDocType(
        Long orgId, Long branchId, String monthKey, DocType docType);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
