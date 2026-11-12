package com.dams.spareparts.service;

import com.dams.common.exception.DamsException;
import com.dams.common.time.OrgTime;
import com.dams.spareparts.dto.SparePartsDtos.BranchInfo;
import com.dams.spareparts.dto.SparePartsDtos.BranchTotal;
import com.dams.spareparts.dto.SparePartsDtos.Dashboard;
import com.dams.spareparts.entity.TransferStatus;
import com.dams.spareparts.repository.InvMovementRepository;
import com.dams.spareparts.repository.InvPartRepository;
import com.dams.spareparts.repository.InvStockRepository;
import com.dams.spareparts.repository.InvTransferRepository;
import com.dams.spareparts.service.SparePartsAccess.Caller;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What is happening across the organization, for the Owner (and the Inventory Manager): stock value per branch,
 * what was billed this month, the state of branch requests and the latest ledger rows. Read-only.
 */
@Service
public class SparePartsDashboardService {

    private static final int RECENT_ROWS = 15;

    private final SparePartsAccess access;
    private final SparePartsStockService stockService;
    private final InvStockRepository stockRepo;
    private final InvPartRepository partRepo;
    private final InvMovementRepository movementRepo;
    private final InvTransferRepository transferRepo;

    public SparePartsDashboardService(SparePartsAccess access,
                                      SparePartsStockService stockService,
                                      InvStockRepository stockRepo,
                                      InvPartRepository partRepo,
                                      InvMovementRepository movementRepo,
                                      InvTransferRepository transferRepo) {
        this.access = access;
        this.stockService = stockService;
        this.stockRepo = stockRepo;
        this.partRepo = partRepo;
        this.movementRepo = movementRepo;
        this.transferRepo = transferRepo;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        Caller caller = access.requireViewer();
        if (!caller.mayOpenDashboard()) {
            throw DamsException.forbidden("The dashboard is for the Owner and the Inventory Manager");
        }
        Long orgId = caller.orgId();

        Map<Long, Object[]> totals = new HashMap<>();
        for (Object[] row : stockRepo.branchTotals(orgId)) {
            totals.put((Long) row[0], row);
        }
        Instant monthStart = OrgTime.today().withDayOfMonth(1).atStartOfDay(OrgTime.ZONE).toInstant();
        Map<Long, Object[]> billed = new HashMap<>();
        for (Object[] row : movementRepo.billedSince(orgId, monthStart)) {
            billed.put((Long) row[0], row);
        }

        List<BranchTotal> branches = new ArrayList<>();
        long units = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (BranchInfo b : access.activeBranches(orgId)) {
            Object[] t = totals.get(b.id());
            Object[] bill = billed.get(b.id());
            int partsHeld = t == null ? 0 : ((Number) t[1]).intValue();
            long branchUnits = t == null ? 0 : ((Number) t[2]).longValue();
            BigDecimal branchValue = t == null ? BigDecimal.ZERO : new BigDecimal(t[3].toString());
            branches.add(new BranchTotal(b.id(), b.code(), b.name(), partsHeld, branchUnits, branchValue,
                bill == null ? 0 : ((Number) bill[1]).longValue(),
                bill == null ? 0 : ((Number) bill[2]).longValue()));
            units += branchUnits;
            value = value.add(branchValue);
        }

        // Every status is listed even when its count is zero, so the screen never has to guess a missing key.
        Map<String, Long> requests = new LinkedHashMap<>();
        for (TransferStatus s : TransferStatus.values()) {
            requests.put(s.name(), 0L);
        }
        for (Object[] row : transferRepo.countByStatus(orgId)) {
            requests.put(((TransferStatus) row[0]).name(), ((Number) row[1]).longValue());
        }

        var recent = stockService.toRows(orgId, movementRepo.findByOrgIdOrderByIdDesc(orgId, PageRequest.of(0, RECENT_ROWS)));
        return new Dashboard(branches, (int) partRepo.countByOrgId(orgId), units, value, requests, recent);
    }
}
