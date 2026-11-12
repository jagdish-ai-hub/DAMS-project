package com.dams.spareparts.repository;

import com.dams.spareparts.entity.InvMovement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/** Read access to the ledger plus the deliberate absence of any update or delete: it is append-only. */
public interface InvMovementRepository extends JpaRepository<InvMovement, Long>, JpaSpecificationExecutor<InvMovement> {

    List<InvMovement> findByOrgIdOrderByIdDesc(Long orgId, Pageable page);

    boolean existsByOrgId(Long orgId);

    /** What the ledger says a branch holds of a part: must always equal inv_stock.quantity. */
    @Query("select coalesce(sum(m.qtyChange), 0) from InvMovement m where m.branchId = :branchId and m.partId = :partId")
    long sumChange(@Param("branchId") Long branchId, @Param("partId") Long partId);

    /** Per branch since a moment: number of bills and units billed. */
    @Query("""
        select m.branchId, count(m), coalesce(sum(-m.qtyChange), 0)
        from InvMovement m
        where m.orgId = :orgId and m.movementType = com.dams.spareparts.entity.MovementType.BILLED
          and m.createdAt >= :since
        group by m.branchId
        """)
    List<Object[]> billedSince(@Param("orgId") Long orgId, @Param("since") Instant since);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
