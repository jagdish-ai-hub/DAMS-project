package com.dams.masters.repository;

import com.dams.masters.entity.ExpenseBusinessStatus;

import java.util.List;

public interface ExpenseBusinessStatusRepository extends OrgMasterRepository<ExpenseBusinessStatus> {

    /** The status(es) marked as moving an expense onto a claim — normally exactly one. */
    List<ExpenseBusinessStatus> findByOrgIdAndTriggersClaimTrue(Long orgId);
}
