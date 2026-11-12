package com.dams.spareparts.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.spareparts.dto.SparePartsDtos.ImportConfirm;
import com.dams.spareparts.dto.SparePartsDtos.ImportPreview;
import com.dams.spareparts.dto.SparePartsDtos.ImportResult;
import com.dams.spareparts.dto.SparePartsDtos.ImportRowIn;
import com.dams.spareparts.dto.SparePartsDtos.ImportRowOut;
import com.dams.spareparts.entity.ImportMode;
import com.dams.spareparts.entity.InvImport;
import com.dams.spareparts.entity.InvPart;
import com.dams.spareparts.entity.InvStock;
import com.dams.spareparts.entity.MovementType;
import com.dams.spareparts.entity.SparePermission;
import com.dams.spareparts.repository.InvImportRepository;
import com.dams.spareparts.repository.InvPartRepository;
import com.dams.spareparts.repository.InvStockRepository;
import com.dams.spareparts.service.SparePartsAccess.Caller;
import com.dams.spareparts.util.SparePartsText;
import com.dams.spareparts.util.XlsxReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stock from an Excel file, in two steps so nothing is posted by surprise: PREVIEW reads the file and shows,
 * row by row, what each row would do or why it is refused; CONFIRM re-checks every row (the screen is not
 * trusted) and posts the good ones in one transaction.
 *
 * The layout is the owner's own sheet: a title line, then a header row with BRANCH, PART ID, PART DESCRIPTION,
 * OPENING QTY, STORAGE BIN, PRICE PER UNIT, TOTAL PRICE, BILLED QTY, JOB CARD, CLOSING QTY. The header row is
 * found by name, wherever it sits, and the stock quantity is CLOSING QTY (what is in the godown now), falling
 * back to OPENING QTY. A row's quantity never reaches the stock list except through the ledger.
 */
@Service
public class SparePartsImportService {

    private static final Logger log = LoggerFactory.getLogger(SparePartsImportService.class);

    /** One upload is limited so a mistake cannot hold the server (1 GB container) busy. */
    static final int MAX_ROWS = 3000;
    private static final int HEADER_SEARCH_ROWS = 20;

    private final SparePartsAccess access;
    private final BranchRepository branchRepo;
    private final InvPartRepository partRepo;
    private final InvStockRepository stockRepo;
    private final InvImportRepository importRepo;
    private final StockLedgerService ledger;

    public SparePartsImportService(SparePartsAccess access,
                                   BranchRepository branchRepo,
                                   InvPartRepository partRepo,
                                   InvStockRepository stockRepo,
                                   InvImportRepository importRepo,
                                   StockLedgerService ledger) {
        this.access = access;
        this.branchRepo = branchRepo;
        this.partRepo = partRepo;
        this.stockRepo = stockRepo;
        this.importRepo = importRepo;
        this.ledger = ledger;
    }

    // --- step 1: preview ---

    @Transactional(readOnly = true)
    public ImportPreview preview(MultipartFile file, ImportMode mode) {
        Caller caller = access.requireViewer();
        access.requirePermission(caller, SparePermission.UPLOAD_ADD);

        List<ImportRowIn> rows = readFile(file);
        List<ImportRowOut> checked = validate(caller, rows, mode);
        return summarize(file.getOriginalFilename(), mode, checked);
    }

    // --- step 2: confirm ---

    @Transactional
    public ImportResult confirm(ImportConfirm request) {
        Caller caller = access.requireViewer();
        access.requirePermission(caller, SparePermission.UPLOAD_ADD);
        if (request.rows().size() > MAX_ROWS) {
            throw DamsException.badRequest("One upload can hold up to " + MAX_ROWS + " rows; this one has " + request.rows().size());
        }

        List<ImportRowOut> checked = validate(caller, request.rows(), request.mode());
        List<ImportRowOut> good = checked.stream().filter(r -> r.error() == null).toList();
        List<ImportRowOut> bad = checked.stream().filter(r -> r.error() != null).toList();

        InvImport header = new InvImport();
        header.setOrgId(caller.orgId());
        header.setFileName(request.fileName());
        header.setMode(request.mode());
        header.setRowsTotal(checked.size());
        header.setRowsPosted(good.size());
        header.setRowsRejected(bad.size());
        header.setCreatedBy(caller.userId());
        header = importRepo.save(header);

        Map<String, Branch> branches = branchesByCode(caller.orgId());
        Map<String, InvPart> parts = loadParts(caller.orgId(), good);
        int newParts = 0;
        for (ImportRowOut row : good) {
            String code = SparePartsText.normalizePartCode(row.partCode());
            InvPart part = parts.get(code);
            StringBuilder note = new StringBuilder("Excel ").append(request.fileName()).append(", row ").append(row.rowNumber());
            if (part == null) {
                part = createPart(caller.orgId(), code, row);
                parts.put(code, part);
                newParts++;
            } else if (row.unitPrice() != null && !row.unitPrice().isBlank()) {
                BigDecimal price = SparePartsText.parsePrice(row.unitPrice());
                if (part.getUnitPrice() == null || part.getUnitPrice().compareTo(price) != 0) {
                    note.append("; price ").append(part.getUnitPrice() == null ? "none" : part.getUnitPrice().toPlainString())
                        .append(" -> ").append(price.toPlainString());
                    part.setUnitPrice(price);
                    partRepo.save(part);
                }
            }
            appendBilledNote(note, row);

            int quantity = SparePartsText.parseQuantity(row.quantity());
            boolean add = request.mode() == ImportMode.ADD;
            ledger.post(new StockLedgerService.Posting(
                caller.orgId(), branches.get(SparePartsText.normalizeBranchCode(row.branchCode())).getId(), part.getId(),
                add ? MovementType.UPLOAD_ADD : MovementType.UPLOAD_SET,
                add ? quantity : null, add ? null : quantity,
                row.bin(), null, null, truncate(note.toString(), 500), header.getId(), null, caller.userId()));
        }

        log.info("Spare Parts import confirmed: orgId={} importId={} file='{}' mode={} total={} posted={} rejected={} newParts={} userId={}",
            caller.orgId(), header.getId(), request.fileName(), request.mode(), checked.size(), good.size(), bad.size(),
            newParts, caller.userId());
        return new ImportResult(header.getId(), good.size(), bad.size(), newParts, bad);
    }

    // --- reading the file ---

    List<ImportRowIn> readFile(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".xlsx")) {
            throw DamsException.badRequest("Please upload an Excel (.xlsx) file. This one is '" + file.getOriginalFilename() + "'.");
        }
        try {
            return readRows(XlsxReader.read(file.getInputStream()));
        } catch (IOException e) {
            throw DamsException.badRequest("That file could not be read: " + e.getMessage());
        }
    }

    /** Picks the first sheet that has a PART ID header and turns its rows into import rows. */
    List<ImportRowIn> readRows(List<XlsxReader.Sheet> sheets) {
        for (XlsxReader.Sheet sheet : sheets) {
            int headerAt = findHeaderRow(sheet);
            if (headerAt < 0) {
                continue;
            }
            return rowsOf(sheet, headerAt);
        }
        throw DamsException.badRequest("No header row with 'PART ID' was found in the first " + HEADER_SEARCH_ROWS
            + " rows of any sheet. The sheet needs headers like BRANCH, PART ID, PART DESCRIPTION, CLOSING QTY, STORAGE BIN, PRICE PER UNIT.");
    }

    private int findHeaderRow(XlsxReader.Sheet sheet) {
        for (int i = 0; i < Math.min(sheet.rows().size(), HEADER_SEARCH_ROWS); i++) {
            for (String cell : sheet.rows().get(i).cells()) {
                if (cell != null && isOneOf(normHeader(cell), "PART ID", "PART NO", "PART NUMBER", "PARTID")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private List<ImportRowIn> rowsOf(XlsxReader.Sheet sheet, int headerAt) {
        XlsxReader.Row header = sheet.rows().get(headerAt);
        int part = column(header, "PART ID", "PART NO", "PART NUMBER", "PARTID");
        int branch = column(header, "BRANCH", "BRANCH CODE");
        int description = column(header, "PART DESCRIPTION", "DESCRIPTION");
        int quantity = column(header, "CLOSING QTY", "CLOSING QUANTITY");
        if (quantity < 0) {
            quantity = column(header, "OPENING QTY", "QTY", "QUANTITY", "STOCK");
        }
        int bin = column(header, "STORAGE BIN", "BIN", "LOCATION");
        int price = column(header, "PRICE PER UNIT", "UNIT PRICE", "PRICE");
        int billed = column(header, "BILLED QTY");
        int jobCard = column(header, "JOB CARD", "JOB CARD NO");

        List<String> missing = new ArrayList<>();
        if (branch < 0) missing.add("BRANCH");
        if (quantity < 0) missing.add("CLOSING QTY (or OPENING QTY)");
        if (!missing.isEmpty()) {
            throw DamsException.badRequest("The sheet '" + sheet.name() + "' has no column for: " + String.join(", ", missing)
                + ". Expected headers like BRANCH, PART ID, PART DESCRIPTION, CLOSING QTY, STORAGE BIN, PRICE PER UNIT.");
        }

        List<ImportRowIn> rows = new ArrayList<>();
        for (int i = headerAt + 1; i < sheet.rows().size(); i++) {
            XlsxReader.Row row = sheet.rows().get(i);
            String branchText = row.cell(branch).trim();
            String partText = row.cell(part).trim();
            if (branchText.isEmpty() && partText.isEmpty()) {
                continue; // a blank line or the TOTAL line at the bottom: not a part
            }
            rows.add(new ImportRowIn(row.number(), branchText, partText,
                row.cell(description).trim(), row.cell(bin).trim(),
                SparePartsText.cleanNumberText(row.cell(quantity)),
                SparePartsText.cleanNumberText(row.cell(price)),
                SparePartsText.cleanNumberText(row.cell(billed)),
                SparePartsText.cleanNumberText(row.cell(jobCard))));
            if (rows.size() > MAX_ROWS) {
                throw DamsException.badRequest("One upload can hold up to " + MAX_ROWS + " rows. Split the file and upload it in parts.");
            }
        }
        if (rows.isEmpty()) {
            throw DamsException.badRequest("The sheet '" + sheet.name() + "' has a header row but no parts under it");
        }
        return rows;
    }

    private static int column(XlsxReader.Row header, String... names) {
        for (int i = 0; i < header.cells().length; i++) {
            String cell = header.cells()[i];
            if (cell != null && isOneOf(normHeader(cell), names)) {
                return i;
            }
        }
        return -1;
    }

    /** "PRICE PER UNIT  " and "Price per unit" and "SL NO." all compare equal to their plain upper-case form. */
    private static String normHeader(String text) {
        return text.toUpperCase().replaceAll("[^A-Z0-9]+", " ").trim();
    }

    private static boolean isOneOf(String value, String... names) {
        for (String name : names) {
            if (value.equals(name)) {
                return true;
            }
        }
        return false;
    }

    // --- checking the rows ---

    /** Every row, in order, either ready to post or carrying the one reason it cannot be. */
    List<ImportRowOut> validate(Caller caller, List<ImportRowIn> rows, ImportMode mode) {
        Long orgId = caller.orgId();
        Map<String, Branch> branches = branchesByCode(orgId);

        Set<String> codes = rows.stream().map(r -> SparePartsText.normalizePartCode(r.partCode()))
            .filter(c -> !c.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, InvPart> parts = codes.isEmpty() ? new HashMap<>()
            : partRepo.findByOrgIdAndPartCodeIn(orgId, codes).stream()
                .collect(Collectors.toMap(InvPart::getPartCode, p -> p, (a, b) -> a, HashMap::new));
        Map<String, Integer> quantities = currentQuantities(orgId, parts.values());

        Map<String, Integer> firstRowOfKey = new HashMap<>();
        List<ImportRowOut> out = new ArrayList<>();
        int position = 0;
        for (ImportRowIn in : rows) {
            position++;
            int rowNumber = in.rowNumber() != null ? in.rowNumber() : position;
            out.add(validateRow(caller, in, rowNumber, mode, branches, parts, quantities, firstRowOfKey));
        }
        return out;
    }

    private ImportRowOut validateRow(Caller caller, ImportRowIn in, int rowNumber, ImportMode mode,
                                     Map<String, Branch> branches, Map<String, InvPart> parts,
                                     Map<String, Integer> quantities, Map<String, Integer> firstRowOfKey) {
        String branchCode = SparePartsText.normalizeBranchCode(in.branchCode());
        String partCode = SparePartsText.normalizePartCode(in.partCode());
        InvPart existing = parts.get(partCode);
        Integer current = null;
        Integer result = null;
        String error = null;
        int quantity = 0;

        Branch branch = branches.get(branchCode);
        if (partCode.isEmpty()) {
            error = "Part ID is missing";
        } else if (partCode.length() > 40) {
            error = "Part ID is longer than 40 characters";
        } else if (branchCode.isEmpty()) {
            error = "Branch is missing";
        } else if (branch == null) {
            error = "'" + branchCode + "' is not a branch of this organization (branches: " + String.join(", ", new java.util.TreeSet<>(branches.keySet())) + ")";
        } else if (!caller.actBranchIds().contains(branch.getId())) {
            error = "You can only change stock at your own branches; " + branchCode + " is not one of yours";
        } else if (SparePartsText.isBlank(in.quantity())) {
            error = "Quantity is missing";
        } else {
            try {
                quantity = SparePartsText.parseQuantity(in.quantity());
                if (!SparePartsText.isBlank(in.unitPrice())) {
                    SparePartsText.parsePrice(in.unitPrice());
                }
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
        }
        if (error == null && existing == null && SparePartsText.isBlank(in.description())) {
            error = "Description is needed because " + partCode + " is a new part";
        }
        if (error == null && in.description() != null && in.description().trim().length() > 255) {
            error = "Description is longer than 255 characters";
        }
        if (error == null && in.bin() != null && in.bin().trim().length() > 60) {
            error = "Storage bin is longer than 60 characters";
        }
        if (error == null) {
            String key = branch.getId() + "|" + partCode;
            Integer firstRow = firstRowOfKey.putIfAbsent(key, rowNumber);
            if (firstRow != null) {
                error = "Same branch and part as row " + firstRow + "; list each part once per branch";
            }
        }
        if (error == null) {
            current = existing == null ? 0 : quantities.getOrDefault(branch.getId() + "|" + existing.getId(), 0);
            result = mode == ImportMode.ADD ? current + quantity : quantity;
        }
        return new ImportRowOut(rowNumber, branchCode, partCode, nullToEmpty(in.description()).trim(),
            nullToEmpty(in.bin()).trim(), nullToEmpty(in.quantity()).trim(), nullToEmpty(in.unitPrice()).trim(),
            nullToEmpty(in.billedQty()).trim(), nullToEmpty(in.jobCard()).trim(),
            current, result, existing == null, error);
    }

    private ImportPreview summarize(String fileName, ImportMode mode, List<ImportRowOut> rows) {
        int errors = (int) rows.stream().filter(r -> r.error() != null).count();
        Set<String> newCodes = rows.stream().filter(r -> r.error() == null && r.newPart())
            .map(ImportRowOut::partCode).collect(Collectors.toSet());
        return new ImportPreview(fileName, mode, rows, rows.size(), rows.size() - errors, errors, newCodes.size());
    }

    // --- helpers ---

    private Map<String, Branch> branchesByCode(Long orgId) {
        Map<String, Branch> byCode = new HashMap<>();
        for (Branch b : branchRepo.findByOrgIdOrderByCodeAsc(orgId)) {
            if (b.isActive()) {
                byCode.put(b.getCode().toUpperCase(), b);
            }
        }
        return byCode;
    }

    /** "branchId|partId" -> quantity, for the parts that already exist. */
    private Map<String, Integer> currentQuantities(Long orgId, java.util.Collection<InvPart> parts) {
        Map<String, Integer> out = new HashMap<>();
        if (parts.isEmpty()) {
            return out;
        }
        Set<Long> ids = parts.stream().map(InvPart::getId).collect(Collectors.toSet());
        for (InvStock s : stockRepo.findByOrgIdAndPartIdIn(orgId, ids)) {
            out.put(s.getBranchId() + "|" + s.getPartId(), s.getQuantity());
        }
        return out;
    }

    private Map<String, InvPart> loadParts(Long orgId, List<ImportRowOut> rows) {
        Set<String> codes = new HashSet<>();
        rows.forEach(r -> codes.add(SparePartsText.normalizePartCode(r.partCode())));
        Map<String, InvPart> out = new HashMap<>();
        if (!codes.isEmpty()) {
            partRepo.findByOrgIdAndPartCodeIn(orgId, codes).forEach(p -> out.put(p.getPartCode(), p));
        }
        return out;
    }

    private InvPart createPart(Long orgId, String code, ImportRowOut row) {
        InvPart part = new InvPart();
        part.setOrgId(orgId);
        part.setPartCode(code);
        part.setDescription(row.description().trim());
        if (row.unitPrice() != null && !row.unitPrice().isBlank()) {
            part.setUnitPrice(SparePartsText.parsePrice(row.unitPrice()));
        }
        return partRepo.save(part);
    }

    /** The sheet's BILLED QTY / JOB CARD columns are history; the closing quantity already reflects them. */
    private static void appendBilledNote(StringBuilder note, ImportRowOut row) {
        boolean hasBilled = row.billedQty() != null && !row.billedQty().isBlank() && !row.billedQty().equals("0");
        if (hasBilled || (row.jobCard() != null && !row.jobCard().isBlank())) {
            note.append("; sheet shows billed ").append(row.billedQty().isBlank() ? "-" : row.billedQty())
                .append(" on job card ").append(row.jobCard().isBlank() ? "-" : row.jobCard());
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
