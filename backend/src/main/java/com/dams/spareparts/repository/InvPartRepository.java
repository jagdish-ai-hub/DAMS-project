package com.dams.spareparts.repository;

import com.dams.spareparts.entity.InvPart;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvPartRepository extends JpaRepository<InvPart, Long> {

    Optional<InvPart> findByIdAndOrgId(Long id, Long orgId);

    Optional<InvPart> findByOrgIdAndPartCode(Long orgId, String partCode);

    List<InvPart> findByOrgIdAndPartCodeIn(Long orgId, Collection<String> partCodes);

    List<InvPart> findByOrgIdAndIdIn(Long orgId, Collection<Long> ids);

    /** Active parts whose code or description contains the text (an empty text matches all), code order. */
    @Query("""
        select p from InvPart p
        where p.orgId = :orgId and p.active = true
          and (:q = '' or lower(p.partCode) like lower(concat('%', :q, '%'))
                       or lower(p.description) like lower(concat('%', :q, '%')))
        order by p.partCode asc
        """)
    List<InvPart> search(@Param("orgId") Long orgId, @Param("q") String q, Pageable page);

    long countByOrgId(Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
