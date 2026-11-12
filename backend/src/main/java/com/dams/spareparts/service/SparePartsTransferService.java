package com.dams.spareparts.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.spareparts.dto.SparePartsDtos.TransferCreate;
import com.dams.spareparts.dto.SparePartsDtos.TransferRow;
import com.dams.spareparts.entity.InvPart;
import com.dams.spareparts.entity.InvStock;
import com.dams.spareparts.entity.InvTransfer;
import com.dams.spareparts.entity.MovementType;
import com.dams.spareparts.entity.SparePermission;
import com.dams.spareparts.entity.TransferStatus;
import com.dams.spareparts.repository.InvPartRepository;
import com.dams.spareparts.repository.InvStockRepository;
import com.dams.spareparts.repository.InvTransferRepository;
import com.dams.spareparts.service.SparePartsAccess.Caller;
import com.dams.spareparts.util.SparePartsText;
import com.dams.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A branch asks another branch for a part:  REQUESTED -> SENT -> RECEIVED   (or DECLINED / CANCELLED).
 *
 * Asking changes nothing. The part leaves the supplying branch's stock when that branch marks it SENT (so it
 * can never be sold twice, and it shows "in transit" while on the road) and joins the requesting branch's stock
 * when that branch marks it RECEIVED. Both moves go through the ledger with the request number as reference.
 */
@Service
public class SparePartsTransferService {

    private static final Logger log = LoggerFactory.getLogger(SparePartsTransferService.class);
    private static final int LIST_LIMIT = 300;

    private final SparePartsAccess access;
    private final BranchRepository branchRepo;
    private final AppUserRepository userRepo;
    private final InvPartRepository partRepo;
    private final InvStockRepository stockRepo;
    private final InvTransferRepository transferRepo;
    private final StockLedgerService ledger;

    public SparePartsTransferService(SparePartsAccess access,
                                     BranchRepository branchRepo,
                                     AppUserRepository userRepo,
                                     InvPartRepository partRepo,
                                     InvStockRepository stockRepo,
                                     InvTransferRepository transferRepo,
                                     StockLedgerService ledger) {
        this.access = access;
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.partRepo = partRepo;
        this.stockRepo = stockRepo;
        this.transferRepo = transferRepo;
        this.ledger = ledger;
    }

    /** Requests of every branch (looking is org-wide), newest first; optionally one status and/or one branch (either side). */
    @Transactional(readOnly = true)
    public List<TransferRow> list(TransferStatus status, Long branchId) {
        Caller caller = access.requireViewer();
        List<InvTransfer> all = transferRepo.findByOrgIdOrderByIdDesc(caller.orgId(), PageRequest.of(0, LIST_LIMIT));
        List<InvTransfer> kept = all.stream()
            .filter(t -> status == null || t.getStatus() == status)
            .filter(t -> branchId == null || t.getFromBranchId().equals(branchId) || t.getToBranchId().equals(branchId))
            .toList();
        return toRows(caller, kept);
    }

    @Transactional
    public TransferRow create(TransferCreate request) {
        Caller caller = access.requireViewer();
        access.require(caller, SparePermission.RAISE_REQUEST, request.toBranchId());
        if (request.fromBranchId().equals(request.toBranchId())) {
            throw DamsException.badRequest("A branch cannot ask itself for a part");
        }
        Branch from = requireActiveBranch(caller.orgId(), request.fromBranchId());
        Branch to = requireActiveBranch(caller.orgId(), request.toBranchId());
        InvPart part = partRepo.findByIdAndOrgId(request.partId(), caller.orgId())
            .orElseThrow(() -> DamsException.notFound("Part", request.partId()));

        int held = stockRepo.findByOrgIdAndBranchIdAndPartId(caller.orgId(), from.getId(), part.getId())
            .map(InvStock::getQuantity).orElse(0);
        if (held < request.quantity()) {
            throw DamsException.badRequest(from.getCode() + " holds " + held + " of " + part.getPartCode()
                + " (" + part.getDescription() + "), so " + request.quantity() + " cannot be requested");
        }

        InvTransfer t = new InvTransfer();
        t.setOrgId(caller.orgId());
        t.setFromBranchId(from.getId());
        t.setToBranchId(to.getId());
        t.setPartId(part.getId());
        t.setQtyRequested(request.quantity());
        String jobCard = SparePartsText.cleanNumberText(request.jobCardNo());
        t.setJobCardNo(jobCard.isEmpty() ? null : jobCard);
        t.setNote(blankToNull(request.note()));
        t.setRequestedBy(caller.userId());
        t = transferRepo.save(t);

        log.info("Request raised: orgId={} requestId={} from={} to={} partId={} quantity={} userId={}",
            caller.orgId(), t.getId(), from.getCode(), to.getCode(), part.getId(), request.quantity(), caller.userId());
        return toRows(caller, List.of(t)).get(0);
    }

    /** The supplying branch sends the part: its stock goes down now, and the part is in transit. */
    @Transactional
    public TransferRow send(Long id, Integer quantity) {
        Caller caller = access.requireViewer();
        InvTransfer t = lockOrFail(caller, id);
        requireStatus(t, TransferStatus.REQUESTED, "sent");
        access.require(caller, SparePermission.SEND_RECEIVE, t.getFromBranchId());

        int qty = quantity == null ? t.getQtyRequested() : quantity;
        if (qty > t.getQtyRequested()) {
            throw DamsException.badRequest("Only " + t.getQtyRequested() + " were requested, so " + qty + " cannot be sent");
        }
        InvPart part = partRepo.findByIdAndOrgId(t.getPartId(), caller.orgId())
            .orElseThrow(() -> DamsException.notFound("Part", t.getPartId()));
        Branch from = requireBranch(caller.orgId(), t.getFromBranchId());
        int held = stockRepo.findByOrgIdAndBranchIdAndPartId(caller.orgId(), from.getId(), part.getId())
            .map(InvStock::getQuantity).orElse(0);
        if (held < qty) {
            throw DamsException.badRequest(from.getCode() + " now holds " + held + " of " + part.getPartCode()
                + ", so " + qty + " cannot be sent");
        }

        ledger.post(new StockLedgerService.Posting(caller.orgId(), from.getId(), part.getId(), MovementType.TRANSFER_OUT,
            -qty, null, null, t.requestNo(), null, "Sent to the requesting branch", null, t.getId(), caller.userId()));

        t.setStatus(TransferStatus.SENT);
        t.setQtySent(qty);
        t.setSentBy(caller.userId());
        t.setSentAt(Instant.now());
        transferRepo.save(t);
        log.info("Request sent: orgId={} requestId={} quantity={} userId={}", caller.orgId(), t.getId(), qty, caller.userId());
        return toRows(caller, List.of(t)).get(0);
    }

    /** The requesting branch confirms the part arrived: its stock goes up. */
    @Transactional
    public TransferRow receive(Long id) {
        Caller caller = access.requireViewer();
        InvTransfer t = lockOrFail(caller, id);
        requireStatus(t, TransferStatus.SENT, "received");
        access.require(caller, SparePermission.SEND_RECEIVE, t.getToBranchId());

        ledger.post(new StockLedgerService.Posting(caller.orgId(), t.getToBranchId(), t.getPartId(), MovementType.TRANSFER_IN,
            t.getQtySent(), null, null, t.requestNo(), null, "Received from the supplying branch", null, t.getId(), caller.userId()));

        t.setStatus(TransferStatus.RECEIVED);
        t.setReceivedBy(caller.userId());
        t.setReceivedAt(Instant.now());
        transferRepo.save(t);
        log.info("Request received: orgId={} requestId={} quantity={} userId={}", caller.orgId(), t.getId(), t.getQtySent(), caller.userId());
        return toRows(caller, List.of(t)).get(0);
    }

    /** The supplying branch says no (with a reason). Nothing moved, so no stock changes. */
    @Transactional
    public TransferRow decline(Long id, String reason) {
        Caller caller = access.requireViewer();
        InvTransfer t = lockOrFail(caller, id);
        requireStatus(t, TransferStatus.REQUESTED, "declined");
        access.require(caller, SparePermission.SEND_RECEIVE, t.getFromBranchId());
        close(t, TransferStatus.DECLINED, reason, caller);
        return toRows(caller, List.of(t)).get(0);
    }

    /** The requesting branch withdraws the request before it was sent. */
    @Transactional
    public TransferRow cancel(Long id, String reason) {
        Caller caller = access.requireViewer();
        InvTransfer t = lockOrFail(caller, id);
        requireStatus(t, TransferStatus.REQUESTED, "cancelled");
        access.require(caller, SparePermission.RAISE_REQUEST, t.getToBranchId());
        close(t, TransferStatus.CANCELLED, reason, caller);
        return toRows(caller, List.of(t)).get(0);
    }

    // --- helpers ---

    private void close(InvTransfer t, TransferStatus status, String reason, Caller caller) {
        t.setStatus(status);
        t.setDeclineReason(reason.trim());
        t.setClosedBy(caller.userId());
        t.setClosedAt(Instant.now());
        transferRepo.save(t);
        log.info("Request {}: orgId={} requestId={} userId={}", status, caller.orgId(), t.getId(), caller.userId());
    }

    private InvTransfer lockOrFail(Caller caller, Long id) {
        return transferRepo.findLocked(id, caller.orgId()).orElseThrow(() -> DamsException.notFound("Request", id));
    }

    private static void requireStatus(InvTransfer t, TransferStatus needed, String action) {
        if (t.getStatus() != needed) {
            throw DamsException.conflict("Request " + t.requestNo() + " is " + t.getStatus() + ", so it cannot be " + action
                + " (it must be " + needed + ")");
        }
    }

    private Branch requireActiveBranch(Long orgId, Long branchId) {
        Branch branch = requireBranch(orgId, branchId);
        if (!branch.isActive()) {
            throw DamsException.badRequest("Branch " + branch.getCode() + " is inactive");
        }
        return branch;
    }

    private Branch requireBranch(Long orgId, Long branchId) {
        return branchRepo.findByIdAndOrgId(branchId, orgId).orElseThrow(() -> DamsException.notFound("Branch", branchId));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Requests with names filled in and, for each, what THIS caller may do next. */
    List<TransferRow> toRows(Caller caller, List<InvTransfer> transfers) {
        Long orgId = caller.orgId();
        Map<Long, InvPart> parts = partRepo.findByOrgIdAndIdIn(orgId, transfers.stream().map(InvTransfer::getPartId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(InvPart::getId, p -> p));
        Map<Long, String> codes = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, Branch::getCode));
        Set<Long> userIds = transfers.stream()
            .flatMap(t -> java.util.stream.Stream.of(t.getRequestedBy(), t.getSentBy(), t.getReceivedBy()))
            .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        userRepo.findAllById(userIds).forEach(u -> names.put(u.getId(), u.getName()));

        List<TransferRow> out = new ArrayList<>();
        for (InvTransfer t : transfers) {
            InvPart part = parts.get(t.getPartId());
            boolean requested = t.getStatus() == TransferStatus.REQUESTED;
            out.add(new TransferRow(t.getId(), t.requestNo(), t.getStatus(),
                t.getFromBranchId(), codes.getOrDefault(t.getFromBranchId(), "#" + t.getFromBranchId()),
                t.getToBranchId(), codes.getOrDefault(t.getToBranchId(), "#" + t.getToBranchId()),
                t.getPartId(), part == null ? "#" + t.getPartId() : part.getPartCode(), part == null ? "" : part.getDescription(),
                t.getQtyRequested(), t.getQtySent(), t.getJobCardNo(), t.getNote(), t.getDeclineReason(),
                name(names, t.getRequestedBy()), t.getRequestedAt(), name(names, t.getSentBy()), t.getSentAt(),
                name(names, t.getReceivedBy()), t.getReceivedAt(),
                requested && access.may(caller, SparePermission.SEND_RECEIVE, t.getFromBranchId()),
                t.getStatus() == TransferStatus.SENT && access.may(caller, SparePermission.SEND_RECEIVE, t.getToBranchId()),
                requested && access.may(caller, SparePermission.SEND_RECEIVE, t.getFromBranchId()),
                requested && access.may(caller, SparePermission.RAISE_REQUEST, t.getToBranchId())));
        }
        return out;
    }

    private static String name(Map<Long, String> names, Long userId) {
        return userId == null ? null : names.getOrDefault(userId, "User #" + userId);
    }
}
