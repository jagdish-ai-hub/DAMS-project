package com.dams.masters.repository;

import com.dams.masters.entity.ExpenseMode;

import java.util.List;

public interface ExpenseModeRepository extends OrgMasterRepository<ExpenseMode> {

    /** Modes that represent physical cash (Cash) — drawer withdrawals on the expense side. */
    List<ExpenseMode> findByOrgIdAndCashTrue(Long orgId);
}
