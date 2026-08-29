package com.dams.masters.repository;

import com.dams.common.entity.OrgMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * Shared read/write surface for the Owner-editable masters. Every method is org-scoped:
 * the "orgFilter" covers list queries, and single-row reads go through
 * {@link #findByIdAndOrgId} so a cross-org id can't be fetched by primary key
 * (Hibernate @Filter does not apply to EntityManager.find()).
 */
@NoRepositoryBean
public interface OrgMasterRepository<T extends OrgMaster> extends JpaRepository<T, Long> {

    List<T> findByOrgIdOrderBySortOrderAscIdAsc(Long orgId);

    Optional<T> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByOrgIdAndNameIgnoreCase(Long orgId, String name);

    Optional<T> findByOrgIdAndNameIgnoreCase(Long orgId, String name);

    /** Used only by the Super Admin org-purge — see admin package. */
    long deleteByOrgId(Long orgId);
}
