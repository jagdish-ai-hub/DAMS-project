package com.dams.spareparts.repository;

import com.dams.spareparts.entity.InvStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvStockRepository extends JpaRepository<InvStock, Long> {

    /**
     * The branch's stock row for a part, locked for the rest of the transaction. Two people billing
     * the last unit at the same moment must queue here, not both succeed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InvStock s where s.orgId = :orgId and s.branchId = :branchId and s.partId = :partId")
    Optional<InvStock> findLocked(@Param("orgId") Long orgId,
                                  @Param("branchId") Long branchId,
                                  @Param("partId") Long partId);

    /**
     * Creates the zero-quantity row if the branch has none yet. ON CONFLICT DO NOTHING (not a
     * try/catch) so two concurrent first uploads cannot poison the transaction with a unique violation.
     */
    @Modifying
    @Query(value = """
        insert into inv_stock (org_id, branch_id, part_id, quantity, updated_at)
        values (:orgId, :branchId, :partId, 0, now())
        on conflict (branch_id, part_id) do nothing
        """, nativeQuery = true)
    int insertIfAbsent(@Param("orgId") Long orgId,
                       @Param("branchId") Long branchId,
                       @Param("partId") Long partId);

    Optional<InvStock> findByOrgIdAndBranchIdAndPartId(Long orgId, Long branchId, Long partId);

    List<InvStock> findByOrgIdAndPartIdIn(Long orgId, Collection<Long> partIds);

    List<InvStock> findByOrgId(Long orgId);

    /** Per branch: parts held (quantity above zero), units, and value at the org-wide part price. */
    @Query("""
        select s.branchId, count(s), coalesce(sum(s.quantity), 0), coalesce(sum(s.quantity * coalesce(p.unitPrice, 0)), 0)
        from InvStock s, InvPart p
        where s.orgId = :orgId and p.id = s.partId and s.quantity > 0
        group by s.branchId
        """)
    List<Object[]> branchTotals(@Param("orgId") Long orgId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
