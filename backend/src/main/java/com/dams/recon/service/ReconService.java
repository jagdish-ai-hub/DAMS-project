package com.dams.recon.service;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.recon.dto.ReconBatchResponse;
import com.dams.recon.dto.ReconLineResponse;
import com.dams.recon.entity.ReconBatch;
import com.dams.recon.entity.ReconLine;
import com.dams.recon.repository.ReconBatchRepository;
import com.dams.recon.repository.ReconLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bank statement reconciliation (FEAT-40). Upload a CSV, get match
 * suggestions per line, confirm or ignore each. Matching NEVER moves money
 * or edits a document — it only explains money. The work product is the
 * unmatched both-sides list: bank credits with no receipt, receipts with
 * no bank credit.
 *
 * <p>Match rules, in order: EXACT = same UTR (last-12 normalised) + same
 * amount; AMOUNT_DATE = same amount within ±2 days (needs a human eye —
 * surfaced as suggestion, never auto-confirmed). One settlement line matches
 * at most one statement line per batch (first wins, rest stay unmatched).
 */
@Service
public class ReconService {

    private static final Logger log = LoggerFactory.getLogger(ReconService.class);
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final ReconBatchRepository batchRepo;
    private final ReconLineRepository lineRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final BranchScope branchScope;
    private final AuditService auditService;

    public ReconService(ReconBatchRepository batchRepo,
                        ReconLineRepository lineRepo,
                        SettlementLineRepository settlementLineRepo,
                        ReceiveDocumentRepository receiveDocumentRepo,
                        BranchScope branchScope,
                        AuditService auditService) {
        this.batchRepo = batchRepo;
        this.lineRepo = lineRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.branchScope = branchScope;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ReconBatchResponse> batches() {
        Long orgId = TenantContext.requireOrgId();
        List<ReconBatchResponse> out = new ArrayList<>();
        for (ReconBatch b : batchRepo.findByOrgIdOrderByUploadedAtDesc(orgId)) {
            int resolved = 0;
            for (ReconLine l : lineRepo.findByOrgIdAndBatchIdOrderByTxnDateAsc(orgId, b.getId())) {
                if (l.isResolved()) {
                    resolved++;
                }
            }
            out.add(ReconBatchResponse.of(b, resolved));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ReconLineResponse> lines(Long batchId) {
        Long orgId = TenantContext.requireOrgId();
        batchRepo.findByIdAndOrgId(batchId, orgId)
            .orElseThrow(() -> DamsException.notFound("Reconciliation batch", batchId));
        List<ReconLineResponse> out = new ArrayList<>();
        for (ReconLine l : lineRepo.findByOrgIdAndBatchIdOrderByTxnDateAsc(orgId, batchId)) {
            out.add(ReconLineResponse.of(l, matchedDocNo(orgId, l.getMatchedSettlementLineId())));
        }
        return out;
    }

    @Transactional
    public ReconBatchResponse upload(MultipartFile file) {
        Long orgId = TenantContext.requireOrgId();
        if (file == null || file.isEmpty()) {
            throw DamsException.badRequest("A statement CSV file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw DamsException.badRequest("Statement file must be under 5 MB");
        }
        List<ParsedLine> parsed = parseCsv(file);
        if (parsed.isEmpty()) {
            throw DamsException.badRequest("No statement lines found — expected header date,utr,amount[,narration]");
        }
        LocalDate min = parsed.stream().map(p -> p.date).min(LocalDate::compareTo).orElseThrow();
        LocalDate max = parsed.stream().map(p -> p.date).max(LocalDate::compareTo).orElseThrow();

        ReconBatch batch = new ReconBatch();
        batch.setOrgId(orgId);
        batch.setFilename(safeFilename(file.getOriginalFilename()));
        batch.setLineCount(parsed.size());
        batch.setUploadedBy(branchScope.currentUserId());
        batch = batchRepo.save(batch);

        // Candidate settlement lines across the statement window, padded ±2 days.
        List<SettlementLine> candidates = settlementLineRepo
            .findByOrgIdAndTransactionDateBetweenAndTransactionRefIsNotNull(
                orgId, min.minusDays(2), max.plusDays(2));
        Map<String, List<SettlementLine>> byUtr = new HashMap<>();
        for (SettlementLine s : candidates) {
            byUtr.computeIfAbsent(normaliseUtr(s.getTransactionRef()), k -> new ArrayList<>()).add(s);
        }
        Map<Long, SettlementLine> byId = new HashMap<>();
        for (SettlementLine s : candidates) {
            byId.put(s.getId(), s);
        }

        java.util.Set<Long> claimed = new java.util.HashSet<>();
        for (ParsedLine p : parsed) {
            ReconLine line = new ReconLine();
            line.setOrgId(orgId);
            line.setBatchId(batch.getId());
            line.setTxnDate(p.date);
            line.setUtr(p.utr);
            line.setAmount(p.amount);
            line.setNarration(p.narration);
            suggest(line, byUtr, byId, claimed);
            if (line.getMatchedSettlementLineId() != null) {
                claimed.add(line.getMatchedSettlementLineId());
            }
            lineRepo.save(line);
        }
        auditService.recordUserEvent("ReconBatch", batch.getId(), EventType.CREATED,
            branchScope.currentUserId(), Map.of("filename", batch.getFilename(), "lines", parsed.size()));
        log.info("Recon uploaded: orgId={} batchId={} lines={} by={}",
            orgId, batch.getId(), parsed.size(), branchScope.currentUserId());
        return ReconBatchResponse.of(batch, 0);
    }

    /** Confirm a suggestion (or record a manual pick the matcher missed). */
    @Transactional
    public ReconLineResponse confirm(Long lineId, Long settlementLineId) {
        Long orgId = TenantContext.requireOrgId();
        ReconLine line = lineRepo.findByIdAndOrgId(lineId, orgId)
            .orElseThrow(() -> DamsException.notFound("Reconciliation line", lineId));
        SettlementLine s = settlementLineRepo.findByIdAndOrgId(settlementLineId, orgId)
            .orElseThrow(() -> DamsException.notFound("Settlement line", settlementLineId));
        line.setMatchedSettlementLineId(s.getId());
        if (line.getMatchKind() == null) {
            line.setMatchKind("MANUAL");
        }
        line.setIgnored(false);
        line = lineRepo.save(line);
        log.info("Recon confirmed: orgId={} lineId={} settlementLineId={} by={}",
            orgId, lineId, s.getId(), branchScope.currentUserId());
        return ReconLineResponse.of(line, matchedDocNo(orgId, s.getId()));
    }

    @Transactional
    public ReconLineResponse ignore(Long lineId, boolean ignored) {
        Long orgId = TenantContext.requireOrgId();
        ReconLine line = lineRepo.findByIdAndOrgId(lineId, orgId)
            .orElseThrow(() -> DamsException.notFound("Reconciliation line", lineId));
        line.setIgnored(ignored);
        if (ignored) {
            line.setMatchedSettlementLineId(null);
            line.setMatchKind(null);
        }
        line = lineRepo.save(line);
        return ReconLineResponse.of(line, matchedDocNo(orgId, line.getMatchedSettlementLineId()));
    }

    // ---------------------------------------------------------- matching

    private void suggest(ReconLine line, Map<String, List<SettlementLine>> byUtr,
                         Map<Long, SettlementLine> byId, java.util.Set<Long> claimed) {
        if (line.getUtr() != null) {
            for (SettlementLine s : byUtr.getOrDefault(normaliseUtr(line.getUtr()), List.of())) {
                if (!claimed.contains(s.getId()) && s.getAmount().compareTo(line.getAmount()) == 0) {
                    line.setMatchedSettlementLineId(s.getId());
                    line.setMatchKind("EXACT");
                    return;
                }
            }
        }
        // Fallback: same amount within ±2 days. Suggested only — the
        // accountant confirms with eyes, the matcher never decides money.
        for (SettlementLine s : byId.values()) {
            if (claimed.contains(s.getId()) || s.getAmount().compareTo(line.getAmount()) != 0) {
                continue;
            }
            long gap = Math.abs(s.getTransactionDate().toEpochDay() - line.getTxnDate().toEpochDay());
            if (gap <= 2) {
                line.setMatchedSettlementLineId(s.getId());
                line.setMatchKind("AMOUNT_DATE");
                return;
            }
        }
    }

    /** Last-12 alphanumerics, upper-cased — bank UTR formats vary by channel. */
    public static String normaliseUtr(String utr) {
        if (utr == null) {
            return "";
        }
        String alnum = utr.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        return alnum.length() > 12 ? alnum.substring(alnum.length() - 12) : alnum;
    }

    private String matchedDocNo(Long orgId, Long settlementLineId) {
        if (settlementLineId == null) {
            return null;
        }
        return settlementLineRepo.findByIdAndOrgId(settlementLineId, orgId)
            .flatMap(s -> receiveDocumentRepo.findByIdAndOrgId(s.getReceiveDocumentId(), orgId))
            .map(d -> d.getDocumentNo()).orElse(null);
    }

    // ---------------------------------------------------------- csv

    private record ParsedLine(LocalDate date, String utr, BigDecimal amount, String narration) {
    }

    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
    };

    private List<ParsedLine> parseCsv(MultipartFile file) {
        List<ParsedLine> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return out;
            }
            String[] cols = header.toLowerCase().split(",");
            int dateIdx = indexOf(cols, "date");
            int utrIdx = indexOf(cols, "utr");
            int amountIdx = indexOf(cols, "amount");
            int narrationIdx = indexOf(cols, "narration");
            if (dateIdx < 0 || amountIdx < 0) {
                throw DamsException.badRequest("Statement CSV needs at least date and amount columns");
            }
            String row;
            int lineNo = 1;
            while ((row = reader.readLine()) != null) {
                lineNo++;
                if (row.isBlank()) {
                    continue;
                }
                String[] cells = row.split(",", -1);
                try {
                    LocalDate date = parseDate(cell(cells, dateIdx));
                    String amountRaw = cell(cells, amountIdx).replaceAll("[,\\s]", "");
                    BigDecimal amount = new BigDecimal(amountRaw);
                    if (amount.signum() == 0) {
                        continue; // zero-value bank lines (charges reversed etc.) explain nothing
                    }
                    out.add(new ParsedLine(date,
                        utrIdx < 0 ? null : blankToNull(cell(cells, utrIdx)),
                        amount,
                        narrationIdx < 0 ? null : blankToNull(cell(cells, narrationIdx))));
                } catch (RuntimeException e) {
                    throw DamsException.badRequest("Statement row " + lineNo + " is not readable (" + e.getMessage() + ")");
                }
            }
        } catch (java.io.IOException e) {
            throw DamsException.badRequest("Could not read the statement file: " + e.getMessage());
        }
        return out;
    }

    private static int indexOf(String[] cols, String name) {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].trim().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String cell(String[] cells, int idx) {
        return idx < cells.length ? cells[idx].trim() : "";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static LocalDate parseDate(String raw) {
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, f);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new IllegalArgumentException("unparseable date '" + raw + "'");
    }

    private static String safeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "statement.csv";
        }
        String base = original.substring(original.replace('\\', '/').lastIndexOf('/') + 1);
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }
}
