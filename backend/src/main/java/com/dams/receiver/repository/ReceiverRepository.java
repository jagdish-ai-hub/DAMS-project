package com.dams.receiver.repository;

import com.dams.receiver.entity.Receiver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiverRepository extends JpaRepository<Receiver, Long> {

    List<Receiver> findByOrgIdOrderByNameAsc(Long orgId);

    Optional<Receiver> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByOrgIdAndNameIgnoreCase(Long orgId, String name);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
