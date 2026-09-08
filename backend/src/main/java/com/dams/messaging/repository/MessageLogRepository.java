package com.dams.messaging.repository;

import com.dams.messaging.entity.MessageLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {

    List<MessageLog> findTop100ByOrgIdOrderByCreatedAtDesc(Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
