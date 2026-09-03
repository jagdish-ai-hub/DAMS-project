package com.dams.attachment.repository;

import com.dams.attachment.entity.Attachment;
import com.dams.attachment.entity.ParentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    Optional<Attachment> findByIdAndOrgId(Long id, Long orgId);

    List<Attachment> findByOrgIdAndParentTypeAndParentIdOrderByUploadedAtAsc(
        Long orgId, ParentType parentType, Long parentId);

    List<Attachment> findByOrgIdAndParentTypeAndParentIdIn(
        Long orgId, ParentType parentType, List<Long> parentIds);

    long countByOrgIdAndParentTypeAndParentId(Long orgId, ParentType parentType, Long parentId);

    /** One grouped count for many parents — {parentId, count} rows. Avoids an N+1 in DTO mapping. */
    @Query("""
        select a.parentId, count(a.id) from Attachment a
        where a.orgId = :orgId and a.parentType = :parentType and a.parentId in :parentIds
        group by a.parentId
        """)
    List<Object[]> countByParentIdIn(@Param("orgId") Long orgId,
                                     @Param("parentType") ParentType parentType,
                                     @Param("parentIds") Collection<Long> parentIds);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
