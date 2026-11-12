package com.dams.spareparts;

import com.dams.spareparts.entity.InvImport;
import com.dams.spareparts.entity.InvMovement;
import com.dams.spareparts.entity.InvPart;
import com.dams.spareparts.entity.InvStock;
import com.dams.spareparts.entity.InvTransfer;
import com.dams.spareparts.entity.TransferStatus;
import com.dams.spareparts.repository.InvImportRepository;
import com.dams.spareparts.repository.InvMovementRepository;
import com.dams.spareparts.repository.InvPartRepository;
import com.dams.spareparts.repository.InvStockRepository;
import com.dams.spareparts.repository.InvTransferRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Spare Parts tables as plain in-memory maps behind the real repository interfaces, so a test can run the REAL
 * services (ledger, stock, transfers, upload) end to end and then check the one rule that matters:
 * a branch's quantity always equals the sum of its ledger rows. Only Spring Data is faked, none of the module's logic.
 */
final class FakeInventory {

    static final long ORG = 1L;

    final Map<String, InvStock> stock = new LinkedHashMap<>();
    final List<InvMovement> ledger = new ArrayList<>();
    final Map<Long, InvTransfer> transfers = new LinkedHashMap<>();
    final Map<Long, InvPart> parts = new LinkedHashMap<>();
    final List<InvImport> imports = new ArrayList<>();

    final InvStockRepository stockRepo = mock(InvStockRepository.class);
    final InvMovementRepository movementRepo = mock(InvMovementRepository.class);
    final InvTransferRepository transferRepo = mock(InvTransferRepository.class);
    final InvPartRepository partRepo = mock(InvPartRepository.class);
    final InvImportRepository importRepo = mock(InvImportRepository.class);

    private long nextId = 100;

    @SuppressWarnings("unchecked")
    FakeInventory() {
        // stock
        when(stockRepo.insertIfAbsent(anyLong(), anyLong(), anyLong())).thenAnswer(inv -> {
            String key = key(inv.getArgument(1), inv.getArgument(2));
            if (stock.containsKey(key)) {
                return 0;
            }
            InvStock s = new InvStock();
            ReflectionTestUtils.setField(s, "id", nextId++);
            s.setOrgId(inv.getArgument(0));
            ReflectionTestUtils.setField(s, "branchId", inv.getArgument(1));
            ReflectionTestUtils.setField(s, "partId", inv.getArgument(2));
            stock.put(key, s);
            return 1;
        });
        when(stockRepo.findLocked(anyLong(), anyLong(), anyLong()))
            .thenAnswer(inv -> Optional.ofNullable(stock.get(key(inv.getArgument(1), inv.getArgument(2)))));
        when(stockRepo.findByOrgIdAndBranchIdAndPartId(anyLong(), anyLong(), anyLong()))
            .thenAnswer(inv -> Optional.ofNullable(stock.get(key(inv.getArgument(1), inv.getArgument(2)))));
        when(stockRepo.findByOrgIdAndPartIdIn(anyLong(), anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(1);
            return stock.values().stream().filter(s -> ids.contains(s.getPartId())).collect(Collectors.toList());
        });
        when(stockRepo.findByOrgId(anyLong())).thenAnswer(inv -> new ArrayList<>(stock.values()));
        when(stockRepo.save(any(InvStock.class))).thenAnswer(inv -> inv.getArgument(0));

        // ledger
        when(movementRepo.save(any(InvMovement.class))).thenAnswer(inv -> {
            InvMovement m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", nextId++);
            ledger.add(m);
            return m;
        });
        when(movementRepo.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(inv -> {
            List<InvMovement> newestFirst = new ArrayList<>(ledger);
            java.util.Collections.reverse(newestFirst);
            return new PageImpl<>(newestFirst);
        });

        // transfers
        when(transferRepo.save(any(InvTransfer.class))).thenAnswer(inv -> {
            InvTransfer t = inv.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", nextId++);
            }
            transfers.put(t.getId(), t);
            return t;
        });
        when(transferRepo.findLocked(anyLong(), anyLong())).thenAnswer(inv -> Optional.ofNullable(transfers.get(inv.<Long>getArgument(0))));
        when(transferRepo.findByOrgIdOrderByIdDesc(anyLong(), any(Pageable.class))).thenAnswer(inv ->
            transfers.values().stream().sorted(Comparator.comparing(InvTransfer::getId).reversed()).collect(Collectors.toList()));
        when(transferRepo.inTransitByPart(anyLong())).thenAnswer(inv -> {
            Map<Long, Integer> sums = new LinkedHashMap<>();
            transfers.values().stream().filter(t -> t.getStatus() == TransferStatus.SENT)
                .forEach(t -> sums.merge(t.getPartId(), t.getQtySent(), Integer::sum));
            List<Object[]> rows = new ArrayList<>();
            sums.forEach((part, qty) -> rows.add(new Object[]{part, (long) qty}));
            return rows;
        });

        // parts
        when(partRepo.save(any(InvPart.class))).thenAnswer(inv -> {
            InvPart p = inv.getArgument(0);
            if (p.getId() == null) {
                ReflectionTestUtils.setField(p, "id", nextId++);
            }
            parts.put(p.getId(), p);
            return p;
        });
        when(partRepo.findByIdAndOrgId(anyLong(), anyLong())).thenAnswer(inv -> Optional.ofNullable(parts.get(inv.<Long>getArgument(0))));
        when(partRepo.findByOrgIdAndPartCode(anyLong(), anyString())).thenAnswer(inv ->
            parts.values().stream().filter(p -> p.getPartCode().equals(inv.getArgument(1))).findFirst());
        when(partRepo.findByOrgIdAndPartCodeIn(anyLong(), anyCollection())).thenAnswer(inv -> {
            java.util.Collection<String> codes = inv.getArgument(1);
            return parts.values().stream().filter(p -> codes.contains(p.getPartCode())).collect(Collectors.toList());
        });
        when(partRepo.findByOrgIdAndIdIn(anyLong(), anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(1);
            return parts.values().stream().filter(p -> ids.contains(p.getId())).collect(Collectors.toList());
        });
        when(partRepo.search(anyLong(), anyString(), any(Pageable.class))).thenAnswer(inv -> {
            String q = inv.<String>getArgument(1).toLowerCase();
            return parts.values().stream()
                .filter(p -> q.isEmpty() || p.getPartCode().toLowerCase().contains(q) || p.getDescription().toLowerCase().contains(q))
                .sorted(Comparator.comparing(InvPart::getPartCode)).collect(Collectors.toList());
        });

        // imports
        when(importRepo.save(any(InvImport.class))).thenAnswer(inv -> {
            InvImport i = inv.getArgument(0);
            if (i.getId() == null) {
                ReflectionTestUtils.setField(i, "id", nextId++);
            }
            imports.add(i);
            return i;
        });
    }

    static String key(Object branchId, Object partId) {
        return branchId + "|" + partId;
    }

    int quantity(long branchId, long partId) {
        InvStock s = stock.get(key(branchId, partId));
        return s == null ? 0 : s.getQuantity();
    }

    long ledgerSum(long branchId, long partId) {
        return ledger.stream().filter(m -> m.getBranchId() == branchId && m.getPartId() == partId)
            .mapToLong(InvMovement::getQtyChange).sum();
    }

    /** The module's core promise: for every branch + part, quantity == sum of its ledger rows (and never below zero). */
    void assertLedgerAddsUp() {
        for (InvStock s : stock.values()) {
            long sum = ledgerSum(s.getBranchId(), s.getPartId());
            if (sum != s.getQuantity() || s.getQuantity() < 0) {
                throw new AssertionError("Branch " + s.getBranchId() + " part " + s.getPartId() + ": quantity "
                    + s.getQuantity() + " but the ledger sums to " + sum);
            }
        }
    }

    InvPart part(String code, String description) {
        InvPart p = new InvPart();
        p.setOrgId(ORG);
        p.setPartCode(code);
        p.setDescription(description);
        return partRepo.save(p);
    }
}
