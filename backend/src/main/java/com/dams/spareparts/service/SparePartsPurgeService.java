package com.dams.spareparts.service;

import com.dams.spareparts.repository.InvImportRepository;
import com.dams.spareparts.repository.InvMovementRepository;
import com.dams.spareparts.repository.InvPartRepository;
import com.dams.spareparts.repository.InvRolePermissionRepository;
import com.dams.spareparts.repository.InvStockRepository;
import com.dams.spareparts.repository.InvTransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a whole organization's Spare Parts data. Called ONLY from the Super Admin organization delete
 * (the one place allowed to reach across tenants), so the platform purge needs no knowledge of this module's tables.
 *
 * Stock history counts as live data: once any ledger row exists the organization cannot be deleted, the same rule
 * the Finance data already follows (plan.md rev 7).
 */
@Service
public class SparePartsPurgeService {

    private static final Logger log = LoggerFactory.getLogger(SparePartsPurgeService.class);

    private final InvMovementRepository movementRepo;
    private final InvTransferRepository transferRepo;
    private final InvImportRepository importRepo;
    private final InvStockRepository stockRepo;
    private final InvRolePermissionRepository permissionRepo;
    private final InvPartRepository partRepo;

    public SparePartsPurgeService(InvMovementRepository movementRepo,
                                  InvTransferRepository transferRepo,
                                  InvImportRepository importRepo,
                                  InvStockRepository stockRepo,
                                  InvRolePermissionRepository permissionRepo,
                                  InvPartRepository partRepo) {
        this.movementRepo = movementRepo;
        this.transferRepo = transferRepo;
        this.importRepo = importRepo;
        this.stockRepo = stockRepo;
        this.permissionRepo = permissionRepo;
        this.partRepo = partRepo;
    }

    /** True once the organization has any stock history. */
    @Transactional(readOnly = true)
    public boolean hasData(long orgId) {
        return movementRepo.existsByOrgId(orgId);
    }

    /** Deletes the organization's Spare Parts rows in foreign-key order (ledger first, parts last). */
    @Transactional
    public void purge(long orgId) {
        long movements = movementRepo.deleteByOrgId(orgId);
        long transfers = transferRepo.deleteByOrgId(orgId);
        long imports = importRepo.deleteByOrgId(orgId);
        long stock = stockRepo.deleteByOrgId(orgId);
        long permissions = permissionRepo.deleteByOrgId(orgId);
        long parts = partRepo.deleteByOrgId(orgId);
        log.info("Spare Parts purge: orgId={} deleted movements={} transfers={} imports={} stock={} permissions={} parts={}",
            orgId, movements, transfers, imports, stock, permissions, parts);
    }
}
