package com.dams.masters.repository;

import com.dams.masters.entity.ReceiveBusinessStatusRole;
import com.dams.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Role grants for job-card business statuses. Org-scoped like every other master read;
 * {@code status_id} alone is never trusted as a lookup key across orgs.
 */
public interface ReceiveBusinessStatusRoleRepository extends JpaRepository<ReceiveBusinessStatusRole, Long> {

    List<ReceiveBusinessStatusRole> findByOrgId(Long orgId);

    List<ReceiveBusinessStatusRole> findByOrgIdAndStatusId(Long orgId, Long statusId);

    boolean existsByOrgIdAndStatusIdAndRole(Long orgId, Long statusId, Role role);

    long deleteByOrgIdAndStatusId(Long orgId, Long statusId);

    /** Used only by the Super Admin org-purge — see admin package. */
    long deleteByOrgId(Long orgId);
}
