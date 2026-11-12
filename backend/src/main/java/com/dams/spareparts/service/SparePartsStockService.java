package com.dams.spareparts.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.spareparts.dto.SparePartsDtos.ActionResult;
import com.dams.spareparts.dto.SparePartsDtos.AddPartRequest;
import com.dams.spareparts.dto.SparePartsDtos.BillRequest;
import com.dams.spareparts.dto.SparePartsDtos.CorrectRequest;
import com.dams.spareparts.dto.SparePartsDtos.MovementRow;
import com.dams.spareparts.dto.SparePartsDtos.StockCell;
import com.dams.spareparts.dto.SparePartsDtos.StockPage;
import com.dams.spareparts.dto.SparePartsDtos.StockRow;
import com.dams.spareparts.entity.InvMovement;
import com.dams.spareparts.entity.InvPart;
import com.dams.spareparts.entity.InvStock;
import com.dams.spareparts.entity.MovementType;
import com.dams.spareparts.entity.SparePermission;
import com.dams.spareparts.repository.InvJobCardLookupRepository;
import com.dams.spareparts.repository.InvMovementRepository;
import com.dams.spareparts.repository.InvPartRepository;
import com.dams.spareparts.repository.InvStockRepository;
import com.dams.spareparts.repository.InvTransferRepository;
import com.dams.spareparts.service.SparePartsAccess.Caller;
import com.dams.spareparts.util.SparePartsText;
import com.dams.user.repository.AppUserRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Looking at stock (every branch), and the single-part actions: add a part, correct a quantity, bill a
 * part to a job card. All quantity changes go through {@link StockLedgerService}.
 */
@Service
public class SparePartsStockService {

    private static final Logger log = LoggerFactory.getLogger(SparePartsStockService.class);

    /** Stock search shows at most this many parts; the screen says so and asks for a narrower search. */
    static final int STOCK_LIMIT = 500;
    private static final int MOVEMENT_DEFAULT_LIMIT = 100;
    private static final int MOVEMENT_MAX_LIMIT = 500;

    private final SparePartsAccess access;
    private final BranchRepository branchRepo;
    private final AppUserRepository userRepo;
    private final InvPartRepository partRepo;
    private final InvStockRepository stockRepo;
    private final InvMovementRepository movementRepo;
    private final InvTransferRepository transferRepo;
    private final InvJobCardLookupRepository jobCardLookup;
    private final StockLedgerService ledger;

    public SparePartsStockService(SparePartsAccess access,
                                  BranchRepository branchRepo,
                                  AppUserRepository userRepo,
                                  InvPartRepository partRepo,
                                  InvStockRepository stockRepo,
                                  InvMovementRepository movementRepo,
                                  InvTransferRepository transferRepo,
                                  InvJobCardLookupRepository jobCardLookup,
                                  StockLedgerService ledger) {
        this.access = access;
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.partRepo = partRepo;
        this.stockRepo = stockRepo;
        this.movementRepo = movementRepo;
        this.transferRepo = transferRepo;
        this.jobCardLookup = jobCardLookup;
        this.ledger = ledger;
    }

    // --- looking ---

    /**
     * Parts matching the text, each with its quantity in every branch that holds it. {@code branchId} keeps only
     * parts that branch holds; {@code inStockOnly} drops parts nobody holds.
     */
    @Transactional(readOnly = true)
    public StockPage search(String q, Long branchId, boolean inStockOnly) {
        Caller caller = access.requireViewer();
        Long orgId = caller.orgId();

        List<InvPart> found = partRepo.search(orgId, q == null ? "" : q.trim(), PageRequest.of(0, STOCK_LIMIT + 1));
        boolean truncated = found.size() > STOCK_LIMIT;
        List<InvPart> parts = truncated ? found.subList(0, STOCK_LIMIT) : found;

        Map<Long, List<InvStock>> stockByPart = parts.isEmpty() ? Map.of()
            : stockRepo.findByOrgIdAndPartIdIn(orgId, parts.stream().map(InvPart::getId).toList()).stream()
                .collect(Collectors.groupingBy(InvStock::getPartId));
        Map<Long, Integer> inTransit = new HashMap<>();
        for (Object[] row : transferRepo.inTransitByPart(orgId)) {
            inTransit.put((Long) row[0], ((Number) row[1]).intValue());
        }

        List<StockRow> rows = new ArrayList<>();
        for (InvPart part : parts) {
            List<InvStock> stock = stockByPart.getOrDefault(part.getId(), List.of());
            int total = stock.stream().mapToInt(InvStock::getQuantity).sum();
            if (inStockOnly && total == 0) {
                continue;
            }
            if (branchId != null && stock.stream().noneMatch(s -> s.getBranchId().equals(branchId) && s.getQuantity() > 0)) {
                continue;
            }
            List<StockCell> cells = stock.stream()
                .sorted(Comparator.comparing(InvStock::getBranchId))
                .map(s -> new StockCell(s.getBranchId(), s.getQuantity(), s.getBin()))
                .toList();
            BigDecimal price = part.getUnitPrice();
            BigDecimal value = price == null ? BigDecimal.ZERO : price.multiply(BigDecimal.valueOf(total));
            rows.add(new StockRow(part.getId(), part.getPartCode(), part.getDescription(), price, cells, total,
                inTransit.getOrDefault(part.getId(), 0), value));
        }
        return new StockPage(rows, truncated);
    }

    /** The ledger, newest first. Everyone who may look at stock may look at its history. */
    @Transactional(readOnly = true)
    public List<MovementRow> movements(Long branchId, Long partId, MovementType type, String reference, String q, Integer limit) {
        Caller caller = access.requireViewer();
        Long orgId = caller.orgId();
        int size = limit == null ? MOVEMENT_DEFAULT_LIMIT : Math.max(1, Math.min(limit, MOVEMENT_MAX_LIMIT));

        Set<Long> partIds = null;
        if (q != null && !q.isBlank()) {
            partIds = partRepo.search(orgId, q.trim(), PageRequest.of(0, STOCK_LIMIT)).stream()
                .map(InvPart::getId).collect(Collectors.toSet());
            if (partIds.isEmpty()) {
                return List.of();
            }
        }
        final Set<Long> partFilter = partIds;
        Specification<InvMovement> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("orgId"), orgId));
            if (branchId != null) all.add(cb.equal(root.get("branchId"), branchId));
            if (partId != null) all.add(cb.equal(root.get("partId"), partId));
            if (type != null) all.add(cb.equal(root.get("movementType"), type));
            if (reference != null && !reference.isBlank()) {
                all.add(cb.like(cb.lower(root.get("reference")), "%" + reference.trim().toLowerCase() + "%"));
            }
            if (partFilter != null) all.add(root.get("partId").in(partFilter));
            return cb.and(all.toArray(new Predicate[0]));
        };
        List<InvMovement> rows = movementRepo.findAll(spec, PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        return toRows(orgId, rows);
    }

    /** Ledger rows with part, branch and user names filled in (a few batched lookups, not one per row). */
    List<MovementRow> toRows(Long orgId, List<InvMovement> rows) {
        Map<Long, InvPart> parts = partRepo.findByOrgIdAndIdIn(orgId, rows.stream().map(InvMovement::getPartId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(InvPart::getId, p -> p));
        Map<Long, String> branchCodes = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, Branch::getCode));
        Set<Long> userIds = rows.stream().map(InvMovement::getCreatedBy).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNames = new HashMap<>();
        userRepo.findAllById(userIds).forEach(u -> userNames.put(u.getId(), u.getName()));

        List<MovementRow> out = new ArrayList<>();
        for (InvMovement m : rows) {
            InvPart part = parts.get(m.getPartId());
            out.add(new MovementRow(m.getId(), m.getCreatedAt(), m.getBranchId(), branchCodes.getOrDefault(m.getBranchId(), "#" + m.getBranchId()),
                m.getPartId(), part == null ? "#" + m.getPartId() : part.getPartCode(), part == null ? "" : part.getDescription(),
                m.getMovementType(), m.getQtyChange(), m.getQtyAfter(), m.getReference(), m.getVehicleNo(), m.getNote(),
                m.getCreatedBy() == null ? "System" : userNames.getOrDefault(m.getCreatedBy(), "User #" + m.getCreatedBy()),
                m.getActingRole()));
        }
        return out;
    }

    // --- acting ---

    /** One part added by hand with an opening quantity (a new part, or more of one that exists). */
    @Transactional
    public ActionResult addPart(AddPartRequest request) {
        Caller caller = access.requireViewer();
        access.require(caller, SparePermission.UPLOAD_ADD, request.branchId());
        Branch branch = requireActiveBranch(caller.orgId(), request.branchId());

        String code = SparePartsText.normalizePartCode(request.partCode());
        if (code.isEmpty()) {
            throw DamsException.badRequest("Part ID is required");
        }
        if (request.unitPrice() != null && request.unitPrice().signum() < 0) {
            throw DamsException.badRequest("Price cannot be below zero");
        }

        InvPart part = partRepo.findByOrgIdAndPartCode(caller.orgId(), code).orElse(null);
        if (part == null) {
            part = new InvPart();
            part.setOrgId(caller.orgId());
            part.setPartCode(code);
            part.setDescription(request.description().trim());
        }
        if (request.unitPrice() != null) {
            part.setUnitPrice(request.unitPrice().setScale(2, java.math.RoundingMode.HALF_UP));
        }
        part = partRepo.save(part);

        StockLedgerService.Posted posted = ledger.post(new StockLedgerService.Posting(
            caller.orgId(), branch.getId(), part.getId(), MovementType.MANUAL_ADD,
            request.quantity(), null, request.bin(), null, null, "Added by hand", null, null, caller.userId()));
        log.info("Part added by hand: orgId={} branchId={} partId={} code={} quantity={} userId={}",
            caller.orgId(), branch.getId(), part.getId(), code, request.quantity(), caller.userId());
        return new ActionResult(part.getId(), posted.after(), posted.movementId());
    }

    /** Sets a quantity to what was really counted. The reason is mandatory; the old number stays in the ledger. */
    @Transactional
    public ActionResult correct(CorrectRequest request) {
        Caller caller = access.requireViewer();
        access.require(caller, SparePermission.CORRECT_QTY, request.branchId());
        Branch branch = requireActiveBranch(caller.orgId(), request.branchId());
        InvPart part = requirePart(caller.orgId(), request.partId());

        StockLedgerService.Posted posted = ledger.post(new StockLedgerService.Posting(
            caller.orgId(), branch.getId(), part.getId(), MovementType.CORRECTION,
            null, request.newQuantity(), null, null, null, request.reason().trim(), null, null, caller.userId()));
        if (posted.movementId() == null) {
            throw DamsException.badRequest(branch.getCode() + " already holds " + posted.after() + " of " + part.getPartCode());
        }
        return new ActionResult(part.getId(), posted.after(), posted.movementId());
    }

    /**
     * Bills a part to a job card: the branch quantity goes down. The job card number is typed (Eicher's own number);
     * if DAMS already knows that number as a CLAIM job card the bill is refused, because local spare parts are only
     * for non-claim jobs (BRD LSP-08). A number DAMS does not know is accepted as typed.
     */
    @Transactional
    public ActionResult bill(BillRequest request) {
        Caller caller = access.requireViewer();
        access.require(caller, SparePermission.BILL_PART, request.branchId());
        Branch branch = requireActiveBranch(caller.orgId(), request.branchId());
        InvPart part = requirePart(caller.orgId(), request.partId());

        String jobCard = SparePartsText.cleanNumberText(request.jobCardNo());
        if (jobCard.isEmpty()) {
            throw DamsException.badRequest("Job card number is required");
        }
        if (jobCardLookup.countClaimJobCards(caller.orgId(), jobCard) > 0) {
            throw DamsException.conflict("Job card " + jobCard + " is a claim job card in DAMS. Local spare parts can only be billed to non-claim jobs.");
        }

        int held = stockRepo.findByOrgIdAndBranchIdAndPartId(caller.orgId(), branch.getId(), part.getId())
            .map(InvStock::getQuantity).orElse(0);
        if (held < request.quantity()) {
            throw DamsException.badRequest(branch.getCode() + " holds " + held + " of " + part.getPartCode()
                + " (" + part.getDescription() + "), so " + request.quantity() + " cannot be billed");
        }

        String vehicle = SparePartsText.normalizeVehicleNo(request.vehicleNo());
        StockLedgerService.Posted posted = ledger.post(new StockLedgerService.Posting(
            caller.orgId(), branch.getId(), part.getId(), MovementType.BILLED,
            -request.quantity(), null, null, jobCard, vehicle.isEmpty() ? null : vehicle,
            request.note() == null || request.note().isBlank() ? null : request.note().trim(), null, null, caller.userId()));
        return new ActionResult(part.getId(), posted.after(), posted.movementId());
    }

    // --- export ---

    /** Every branch + part stock row as CSV, in the owner's own sheet layout so the file can be uploaded again. */
    @Transactional(readOnly = true)
    public byte[] exportCsv() {
        Caller caller = access.requireViewer();
        if (!caller.isOwner()) {
            access.requirePermission(caller, SparePermission.EXPORT);
        }
        Long orgId = caller.orgId();
        Map<Long, String> branchCodes = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, Branch::getCode));
        List<InvStock> stock = stockRepo.findByOrgId(orgId);
        Map<Long, InvPart> parts = partRepo.findByOrgIdAndIdIn(orgId, stock.stream().map(InvStock::getPartId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(InvPart::getId, p -> p));

        stock.sort(Comparator.comparing((InvStock s) -> branchCodes.getOrDefault(s.getBranchId(), ""))
            .thenComparing(s -> parts.get(s.getPartId()) == null ? "" : parts.get(s.getPartId()).getPartCode()));

        StringBuilder csv = new StringBuilder("﻿");   // BOM so Excel reads UTF-8
        csv.append("SL NO,BRANCH,PART ID,PART DESCRIPTION,STORAGE BIN,PRICE PER UNIT,CLOSING QTY,TOTAL PRICE\r\n");
        int serial = 0;
        for (InvStock s : stock) {
            InvPart p = parts.get(s.getPartId());
            if (p == null) {
                continue;
            }
            BigDecimal price = p.getUnitPrice();
            csv.append(++serial).append(',')
                .append(cell(branchCodes.get(s.getBranchId()))).append(',')
                .append(cell(p.getPartCode())).append(',')
                .append(cell(p.getDescription())).append(',')
                .append(cell(s.getBin())).append(',')
                .append(price == null ? "" : price.toPlainString()).append(',')
                .append(s.getQuantity()).append(',')
                .append(price == null ? "" : price.multiply(BigDecimal.valueOf(s.getQuantity())).toPlainString())
                .append("\r\n");
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * One CSV text cell. Quoted when it holds a comma, quote or line break; and a cell that starts with = + - or @
     * gets a leading apostrophe so Excel shows it as text instead of running it as a formula.
     */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String text = value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    // --- helpers ---

    private Branch requireActiveBranch(Long orgId, Long branchId) {
        Branch branch = branchRepo.findByIdAndOrgId(branchId, orgId)
            .orElseThrow(() -> DamsException.notFound("Branch", branchId));
        if (!branch.isActive()) {
            throw DamsException.badRequest("Branch " + branch.getCode() + " is inactive");
        }
        return branch;
    }

    private InvPart requirePart(Long orgId, Long partId) {
        return partRepo.findByIdAndOrgId(partId, orgId).orElseThrow(() -> DamsException.notFound("Part", partId));
    }
}
