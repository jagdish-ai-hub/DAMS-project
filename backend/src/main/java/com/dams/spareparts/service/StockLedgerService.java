package com.dams.spareparts.service;

import com.dams.common.exception.DamsException;
import com.dams.common.security.ActingDetails;
import com.dams.spareparts.entity.InvMovement;
import com.dams.spareparts.entity.InvStock;
import com.dams.spareparts.entity.MovementType;
import com.dams.spareparts.repository.InvMovementRepository;
import com.dams.spareparts.repository.InvStockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The ONLY code that changes a quantity. Every change writes one ledger row and moves the branch
 * quantity by the same amount in the same transaction, so quantity == sum of the ledger, always.
 *
 * Nobody types over a quantity: an upload, a bill, a correction and a transfer all arrive here as a
 * signed change. A mistake is undone by a new, opposite row, never by an edit (the ledger is append-only,
 * like DAMS settlement lines). The method needs an open transaction (MANDATORY) so the caller's own work,
 * for example marking a request Sent, commits or rolls back together with the stock change.
 */
@Service
public class StockLedgerService {

    private static final Logger log = LoggerFactory.getLogger(StockLedgerService.class);

    private final InvStockRepository stockRepo;
    private final InvMovementRepository movementRepo;

    public StockLedgerService(InvStockRepository stockRepo, InvMovementRepository movementRepo) {
        this.stockRepo = stockRepo;
        this.movementRepo = movementRepo;
    }

    /**
     * One stock change. Give either {@code delta} (add or take this many) or {@code setTo} (make the
     * quantity this, and record the difference); never both.
     */
    public record Posting(Long orgId, Long branchId, Long partId, MovementType type,
                          Integer delta, Integer setTo, String bin,
                          String reference, String vehicleNo, String note,
                          Long importId, Long transferId, Long userId) {
    }

    /** {@code movementId} is null when the quantity did not change (so no ledger row was needed). */
    public record Posted(int before, int after, Long movementId) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Posted post(Posting p) {
        if ((p.delta() == null) == (p.setTo() == null)) {
            throw new IllegalArgumentException("A stock posting needs exactly one of delta or setTo");
        }

        // The row is created at zero if the branch never held this part, then locked so two people
        // changing the same part at the same moment queue up instead of overwriting each other.
        stockRepo.insertIfAbsent(p.orgId(), p.branchId(), p.partId());
        InvStock stock = stockRepo.findLocked(p.orgId(), p.branchId(), p.partId())
            .orElseThrow(() -> DamsException.notFound("Stock row for part " + p.partId() + " at branch", p.branchId()));

        int before = stock.getQuantity();
        int change = p.setTo() != null ? p.setTo() - before : p.delta();
        int after = before + change;
        if (after < 0) {
            throw DamsException.badRequest("Branch " + p.branchId() + " holds " + before + " of part " + p.partId()
                + " (org " + p.orgId() + "), so it cannot be reduced by " + (-change));
        }

        if (p.bin() != null && !p.bin().isBlank()) {
            stock.setBin(p.bin().trim());
        }
        stock.setQuantity(after);
        stock.setUpdatedAt(Instant.now());
        stockRepo.save(stock);

        if (change == 0) {
            return new Posted(before, after, null);
        }

        InvMovement row = new InvMovement();
        row.setOrgId(p.orgId());
        row.setBranchId(p.branchId());
        row.setPartId(p.partId());
        row.setMovementType(p.type());
        row.setQtyChange(change);
        row.setQtyAfter(after);
        row.setReference(p.reference());
        row.setVehicleNo(p.vehicleNo());
        row.setNote(p.note());
        row.setImportId(p.importId());
        row.setTransferId(p.transferId());
        row.setCreatedBy(p.userId());
        row.setActingRole(ActingDetails.isActing() ? ActingDetails.actingRole().name() : null);
        row = movementRepo.save(row);

        log.info("Stock moved: orgId={} branchId={} partId={} type={} change={} before={} after={} reference={} movementId={}",
            p.orgId(), p.branchId(), p.partId(), p.type(), change, before, after, p.reference(), row.getId());
        return new Posted(before, after, row.getId());
    }
}
