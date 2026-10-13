package com.dams.receiver.repository;

import com.dams.receiver.entity.Receiver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiverRepository extends JpaRepository<Receiver, Long> {

    List<Receiver> findByOrgIdOrderByNameAsc(Long orgId);

    Optional<Receiver> findByIdAndOrgId(Long id, Long orgId);

    /** Batch id lookup scoped by org — resolves many receivers in one query (My Entries, queues). */
    List<Receiver> findByOrgIdAndIdIn(Long orgId, java.util.Collection<Long> ids);

    /** Inline receiver create dedups on name (case-insensitive), like Customer. */
    Optional<Receiver> findFirstByOrgIdAndNameIgnoreCase(Long orgId, String name);

    boolean existsByOrgIdAndNameIgnoreCase(Long orgId, String name);

    /** Vendor search (rev 76): active vendors whose name or phone contains the text, name order. */
    @org.springframework.data.jpa.repository.Query("""
        select r from Receiver r
        where r.orgId = :orgId and r.active = true
          and (:q = '' or lower(r.name) like lower(concat('%', :q, '%')) or coalesce(r.phone, '') like concat('%', :q, '%'))
        order by r.name asc
        """)
    List<Receiver> searchActive(@org.springframework.data.repository.query.Param("orgId") Long orgId,
                                @org.springframework.data.repository.query.Param("q") String q,
                                org.springframework.data.domain.Pageable page);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
