package com.dams.messaging.repository;

import com.dams.messaging.entity.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

    List<MessageTemplate> findByOrgIdOrderByCodeAsc(Long orgId);

    Optional<MessageTemplate> findByIdAndOrgId(Long id, Long orgId);

    Optional<MessageTemplate> findByOrgIdAndCode(Long orgId, String code);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
