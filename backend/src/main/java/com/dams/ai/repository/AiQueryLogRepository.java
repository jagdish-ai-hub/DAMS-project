package com.dams.ai.repository;

import com.dams.ai.entity.AiQueryLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQueryLogRepository extends JpaRepository<AiQueryLog, Long> {

    void deleteByOrgId(Long orgId);
}
