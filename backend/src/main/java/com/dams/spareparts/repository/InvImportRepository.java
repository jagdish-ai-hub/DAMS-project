package com.dams.spareparts.repository;

import com.dams.spareparts.entity.InvImport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvImportRepository extends JpaRepository<InvImport, Long> {

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
