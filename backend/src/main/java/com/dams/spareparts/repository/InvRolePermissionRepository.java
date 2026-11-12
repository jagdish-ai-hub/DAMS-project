package com.dams.spareparts.repository;

import com.dams.spareparts.entity.InvRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvRolePermissionRepository extends JpaRepository<InvRolePermission, Long> {

    List<InvRolePermission> findByOrgId(Long orgId);

    List<InvRolePermission> findByOrgIdAndRole(Long orgId, String role);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
