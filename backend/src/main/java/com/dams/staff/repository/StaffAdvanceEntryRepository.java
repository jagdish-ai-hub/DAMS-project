package com.dams.staff.repository;

import com.dams.staff.entity.StaffAdvanceEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface StaffAdvanceEntryRepository extends JpaRepository<StaffAdvanceEntry, Long> {

    List<StaffAdvanceEntry> findByOrgIdAndStaffIdOrderByTxnDateDescIdDesc(Long orgId, Long staffId);

    /** Outstanding = advances − recoveries, derived so it can never drift. */
    @Query("SELECT COALESCE(SUM(CASE WHEN e.kind = 'ADVANCE' THEN e.amount ELSE e.amount * -1 END), 0) "
        + "FROM StaffAdvanceEntry e WHERE e.orgId = :orgId AND e.staffId = :staffId")
    BigDecimal outstandingFor(Long orgId, Long staffId);

    /** Super Admin org-purge only. */
    long deleteByOrgId(Long orgId);
}
