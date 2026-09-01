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

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
