package com.dams.attachment.repository;

import com.dams.attachment.entity.Attachment;
import com.dams.attachment.entity.ParentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByIdAndOrgId(Long id, Long orgId);

    List<Attachment> findByOrgIdAndParentTypeAndParentIdOrderByUploadedAtAsc(
        Long orgId, ParentType parentType, Long parentId);

    List<Attachment> findByOrgIdAndParentTypeAndParentIdIn(
        Long orgId, ParentType parentType, List<Long> parentIds);

    long countByOrgIdAndParentTypeAndParentId(Long orgId, ParentType parentType, Long parentId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
