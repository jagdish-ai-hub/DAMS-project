package com.dams.masters.repository;

import com.dams.masters.entity.SettlementMode;

import java.util.List;

public interface SettlementModeRepository extends OrgMasterRepository<SettlementMode> {

    /** Modes that represent physical cash (Cash, Adv-Cash) — the ones that feed the drawer. */
    List<SettlementMode> findByOrgIdAndCashTrue(Long orgId);
}
