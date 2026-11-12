package com.dams.spareparts.repository;

import com.dams.spareparts.entity.InvTransfer;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvTransferRepository extends JpaRepository<InvTransfer, Long> {

    /** Locked so two clicks on Send (or Send and Cancel) cannot both act on the same request. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InvTransfer t where t.id = :id and t.orgId = :orgId")
    Optional<InvTransfer> findLocked(@Param("id") Long id, @Param("orgId") Long orgId);

    Optional<InvTransfer> findByIdAndOrgId(Long id, Long orgId);

    List<InvTransfer> findByOrgIdOrderByIdDesc(Long orgId, Pageable page);

    /** Request counts by status, for the Owner dashboard. */
    @Query("select t.status, count(t) from InvTransfer t where t.orgId = :orgId group by t.status")
    List<Object[]> countByStatus(@Param("orgId") Long orgId);

    /** Units currently on the road, per part (status SENT). */
    @Query("""
        select t.partId, coalesce(sum(t.qtySent), 0)
        from InvTransfer t
        where t.orgId = :orgId and t.status = com.dams.spareparts.entity.TransferStatus.SENT
        group by t.partId
        """)
    List<Object[]> inTransitByPart(@Param("orgId") Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
