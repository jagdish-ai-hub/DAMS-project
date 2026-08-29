package com.dams.audit.repository;

import com.dams.audit.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
        Long orgId, String entityType, Long entityId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
