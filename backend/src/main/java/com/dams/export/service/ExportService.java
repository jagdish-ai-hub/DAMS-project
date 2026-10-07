package com.dams.export.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.masters.entity.Bank;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.ExpenseSubCategory;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.*;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.review.dto.ReviewQueueItem;
import com.dams.review.service.ReviewService;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tally / Excel ledger export service (Stage 12 / FEAT-04).
 * Emits RFC-4180 CSV with UTF-8 BOM so Excel opens with correct formatting.
 * Strictly branch-scoped and tenant-isolated by authenticated user.
 */
@Service
public class ExportService {

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final SettlementLineRepository settlementLineRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final BranchRepository branchRepo;
    private final CustomerRepository customerRepo;
    private final ReceiverRepository receiverRepo;
    private final BankRepository bankRepo;
    private final SettlementModeRepository settlementModeRepo;
    private final ExpenseModeRepository expenseModeRepo;
    private final ExpenseCategoryRepository categoryRepo;
    private final ExpenseSubCategoryRepository subCategoryRepo;
    private final VehicleRepository vehicleRepo;
    private final BranchScope branchScope;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final CashDocumentRepository cashDocumentRepo;
    private final JobCardRepository jobCardRepo;
    private final ReviewService reviewService;

    public ExportService(SettlementLineRepository settlementLineRepo,
                         ExpenseLineRepository expenseLineRepo,
                         BranchRepository branchRepo,
                         CustomerRepository customerRepo,
                         ReceiverRepository receiverRepo,
                         BankRepository bankRepo,
                         SettlementModeRepository settlementModeRepo,
                         ExpenseModeRepository expenseModeRepo,
                         ExpenseCategoryRepository categoryRepo,
                         ExpenseSubCategoryRepository subCategoryRepo,
                         VehicleRepository vehicleRepo,
                         BranchScope branchScope,
                         ReceiveDocumentRepository receiveDocumentRepo,
                         ExpenseDocumentRepository expenseDocumentRepo,
                         CashDocumentRepository cashDocumentRepo,
                         JobCardRepository jobCardRepo,
                         ReviewService reviewService) {
        this.settlementLineRepo = settlementLineRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.branchRepo = branchRepo;
        this.customerRepo = customerRepo;
        this.receiverRepo = receiverRepo;
        this.bankRepo = bankRepo;
        this.settlementModeRepo = settlementModeRepo;
        this.expenseModeRepo = expenseModeRepo;
        this.categoryRepo = categoryRepo;
        this.subCategoryRepo = subCategoryRepo;
        this.vehicleRepo = vehicleRepo;
        this.branchScope = branchScope;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.cashDocumentRepo = cashDocumentRepo;
        this.jobCardRepo = jobCardRepo;
        this.reviewService = reviewService;
    }

    @Transactional(readOnly = true)
    public byte[] exportReceiptsCsv(Long requestedBranchId, LocalDate fromDate, LocalDate toDate) {
        Long orgId = TenantContext.requireOrgId();
        Collection<Long> branches = resolveBranches(orgId, requestedBranchId);
        LocalDate from = fromDate != null ? fromDate : LocalDate.now().minusDays(90);
        LocalDate to = toDate != null ? toDate : LocalDate.now();

        List<Object[]> rows = settlementLineRepo.findLinesForExport(orgId, branches, from, to);
        return renderReceiptsCsv(orgId, rows);
    }

    /**
     * Same receipt export, keyed by explicit receipt ids instead of a branch/date window —
     * the Accountant's "export exactly what's selected" on the Direct Approve list. Rows
     * outside the caller's branch access are dropped rather than erroring, since the id list
     * came from a query the caller's own access already filtered.
     */
    @Transactional(readOnly = true)
    public byte[] exportReceiptsCsvByIds(Collection<Long> receiptIds) {
        Long orgId = TenantContext.requireOrgId();
        if (receiptIds == null || receiptIds.isEmpty()) {
            return renderReceiptsCsv(orgId, List.of());
        }
        List<Object[]> rows = settlementLineRepo.findLinesForExportByReceiptIds(orgId, receiptIds).stream()
            .filter(r -> branchScope.canSeeBranch(((ReceiveDocument) r[1]).getBranchId()))
            .toList();
        return renderReceiptsCsv(orgId, rows);
    }

    private byte[] renderReceiptsCsv(Long orgId, List<Object[]> rows) {
        Map<Long, String> branchCodes = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, Branch::getCode, (a, b) -> a));
        Map<Long, String> modes = settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(SettlementMode::getId, SettlementMode::getName, (a, b) -> a));
        Map<Long, String> banks = bankRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(Bank::getId, Bank::getName, (a, b) -> a));
        Map<Long, Customer> customers = customerRepo.findByOrgIdOrderByNameAsc(orgId).stream()
            .collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));

        Set<Long> vehicleIds = rows.stream()
            .map(r -> ((JobCard) r[2]).getVehicleId())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, String> vehicleNumbers = vehicleIds.isEmpty() ? Collections.emptyMap() :
            vehicleRepo.findByOrgIdAndIdIn(orgId, vehicleIds).stream()
                .collect(Collectors.toMap(Vehicle::getId, Vehicle::getVehicleNo, (a, b) -> a));

        StringBuilder sb = new StringBuilder();
        sb.append("Voucher Date,Document No,Line ID,Branch,Customer Name,Customer Phone,Vehicle No,Job Card Ref,DBM ID,Invoice No,Mode,Bank Name,Transaction Ref,Amount,Status,Remarks\n");

        for (Object[] row : rows) {
            SettlementLine line = (SettlementLine) row[0];
            ReceiveDocument doc = (ReceiveDocument) row[1];
            JobCard jc = (JobCard) row[2];
            Customer cust = jc.getCustomerId() == null ? null : customers.get(jc.getCustomerId());
            String bCode = branchCodes.getOrDefault(doc.getBranchId(), "?");
            String jcRef = bCode + "-JC-" + jc.getId();

            sb.append(escape(line.getTransactionDate().toString())).append(',');
            sb.append(escape(doc.getDocumentNo() != null ? doc.getDocumentNo() : "#" + doc.getId())).append(',');
            sb.append(escape(line.getLineId() != null ? line.getLineId() : "L" + line.getLineNo())).append(',');
            sb.append(escape(bCode)).append(',');
            sb.append(escape(cust != null ? cust.getName() : "")).append(',');
            sb.append(escape(cust != null && cust.getPhone() != null ? cust.getPhone() : "")).append(',');
            sb.append(escape(jc.getVehicleId() != null ? vehicleNumbers.getOrDefault(jc.getVehicleId(), "") : "")).append(',');
            sb.append(escape(jcRef)).append(',');
            sb.append(escape(jc.getDbmId() != null ? jc.getDbmId() : "")).append(',');
            sb.append(escape(jc.getInvoiceNo() != null ? jc.getInvoiceNo() : "")).append(',');
            sb.append(escape(modes.getOrDefault(line.getSettlementModeId(), "Payment"))).append(',');
            sb.append(escape(line.getBankId() != null ? banks.getOrDefault(line.getBankId(), "") : "")).append(',');
            sb.append(escape(line.getTransactionRef() != null ? line.getTransactionRef() : "")).append(',');
            sb.append(line.getAmount().toPlainString()).append(',');
            sb.append(escape(doc.getWorkflowStatus().name())).append(',');
            sb.append(escape(line.getRemark() != null ? line.getRemark() : "")).append('\n');
        }

        return withBom(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Transactional(readOnly = true)
    public byte[] exportExpensesCsv(Long requestedBranchId, LocalDate fromDate, LocalDate toDate) {
        Long orgId = TenantContext.requireOrgId();
        Collection<Long> branches = resolveBranches(orgId, requestedBranchId);
        LocalDate from = fromDate != null ? fromDate : LocalDate.now().minusDays(90);
        LocalDate to = toDate != null ? toDate : LocalDate.now();

        List<Object[]> rows = expenseLineRepo.findLinesForExport(orgId, branches, from, to);

        Map<Long, String> branchCodes = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, Branch::getCode, (a, b) -> a));
        Map<Long, String> receivers = receiverRepo.findByOrgIdOrderByNameAsc(orgId).stream()
            .collect(Collectors.toMap(Receiver::getId, Receiver::getName, (a, b) -> a));
        Map<Long, String> modes = expenseModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(ExpenseMode::getId, ExpenseMode::getName, (a, b) -> a));
        Map<Long, String> banks = bankRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(Bank::getId, Bank::getName, (a, b) -> a));
        Map<Long, String> categories = categoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getName, (a, b) -> a));
        Map<Long, String> subCategories = subCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(ExpenseSubCategory::getId, ExpenseSubCategory::getName, (a, b) -> a));

        StringBuilder sb = new StringBuilder();
        sb.append("Voucher Date,Document No,Line ID,Branch,Receiver / Vendor,Category,Sub-Category,Mode,Bank Name,Transaction Ref,Amount,Status,Remarks\n");

        for (Object[] row : rows) {
            ExpenseLine line = (ExpenseLine) row[0];
            ExpenseDocument doc = (ExpenseDocument) row[1];

            sb.append(escape(line.getTransactionDate().toString())).append(',');
            sb.append(escape(doc.getDocumentNo() != null ? doc.getDocumentNo() : "#" + doc.getId())).append(',');
            sb.append(escape(line.getLineId() != null ? line.getLineId() : "L" + line.getLineNo())).append(',');
            sb.append(escape(branchCodes.getOrDefault(doc.getBranchId(), "?"))).append(',');
            sb.append(escape(receivers.getOrDefault(doc.getReceiverId(), "Vendor"))).append(',');
            sb.append(escape(categories.getOrDefault(doc.getExpenseCategoryId(), "Expense"))).append(',');
            sb.append(escape(line.getSubCategoryId() != null ? subCategories.getOrDefault(line.getSubCategoryId(), "") : "")).append(',');
            sb.append(escape(modes.getOrDefault(line.getExpenseModeId(), "Payment"))).append(',');
            sb.append(escape(line.getBankId() != null ? banks.getOrDefault(line.getBankId(), "") : "")).append(',');
            sb.append(escape(line.getTransactionRef() != null ? line.getTransactionRef() : "")).append(',');
            sb.append(line.getAmount().toPlainString()).append(',');
            sb.append(escape(doc.getWorkflowStatus().name())).append(',');
            sb.append(escape(line.getRemark() != null ? line.getRemark() : "")).append('\n');
        }

        return withBom(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ============================================================ on-screen list export (rev 67)

    private static final String[] RECEIPT_LIST_HEADER = {
        "Status", "Workflow Status", "Submitted On", "Document No", "Branch", "Customer", "Customer Phone",
        "Vehicle No", "Job Card Ref", "DBM ID", "Invoice No", "Invoice Amount", "Category", "Claim",
        "Transaction Amount", "Line ID", "Line Date", "Mode", "Bank Name", "Transaction Ref", "Line Amount",
        "Original Amount", "Override Reason", "Line Remarks"};

    private static final String[] EXPENSE_LIST_HEADER = {
        "Status", "Workflow Status", "Submitted On", "Document No", "Branch", "Receiver / Vendor", "Category",
        "Customer", "Vehicle No", "Job Card Ref", "DBM ID", "Invoice No", "Over Limit", "Pre-Approved", "Claim",
        "Transaction Amount", "Line ID", "Line Date", "Sub-Category", "Mode", "Bank Name", "Transaction Ref",
        "Line Amount", "Original Amount", "Override Reason", "Line Remarks"};

    private static final String[] CASH_LIST_HEADER = {
        "Status", "Workflow Status", "Submitted On", "Document No", "Branch", "Direction", "Transaction Date",
        "Bank Name", "Transaction Ref", "Amount", "Remarks"};

    /**
     * The Accountant's "Pending & closed" window, exported exactly as shown (AGENT.md #5, rev 67):
     * these documents, in this order. One row per settlement / expense line with the transaction's
     * details repeated; a transaction with no lines still gets one row. "Transaction Amount" is
     * written on a transaction's first row only, so the column sums to the window's total (also
     * written as a closing Total row). Party, category and amount come from {@link ReviewService}'s
     * queue rows, so they match the screen by construction. Ids outside the caller's branches, or in
     * a state that window never lists (draft, queried, rejected), are dropped.
     */
    @Transactional(readOnly = true)
    public byte[] exportReviewListCsv(String type, List<Long> ids) {
        Long orgId = TenantContext.requireOrgId();
        List<Long> order = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        String csv = switch (type == null ? "" : type) {
            case "receipt" -> receiptListCsv(orgId, order);
            case "expense" -> expenseListCsv(orgId, order);
            case "cash" -> cashListCsv(orgId, order);
            default -> throw DamsException.badRequest("Unknown list type: " + type + " (expected receipt, expense or cash)");
        };
        return withBom(csv.getBytes(StandardCharsets.UTF_8));
    }

    private String receiptListCsv(Long orgId, List<Long> order) {
        List<ReceiveDocument> docs = order.isEmpty() ? List.of() : inScreenOrder(order,
            receiveDocumentRepo.findByOrgIdAndIdIn(orgId, order), ReceiveDocument::getId, ReceiveDocument::getBranchId,
            d -> d.getWorkflowStatus().name());
        List<ReviewQueueItem> items = reviewService.toReceiptItems(orgId, docs);
        List<Long> docIds = docs.stream().map(ReceiveDocument::getId).toList();

        Map<Long, JobCard> jobCards = docs.isEmpty() ? Map.of() :
            jobCardRepo.findByOrgIdAndIdIn(orgId, docs.stream().map(ReceiveDocument::getJobCardId).distinct().toList())
                .stream().collect(Collectors.toMap(JobCard::getId, j -> j, (a, b) -> a));
        Map<Long, Customer> customers = customersById(orgId, jobCards.values().stream().map(JobCard::getCustomerId).toList());
        Map<Long, String> vehicles = vehicleNosById(orgId, jobCards.values().stream().map(JobCard::getVehicleId).toList());
        Map<Long, List<SettlementLine>> linesByDoc = docIds.isEmpty() ? Map.of() :
            settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(orgId, docIds).stream()
                .collect(Collectors.groupingBy(SettlementLine::getReceiveDocumentId));
        Map<Long, String> modes = settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(SettlementMode::getId, SettlementMode::getName, (a, b) -> a));
        Map<Long, String> banks = bankNames(orgId);

        StringBuilder sb = new StringBuilder();
        row(sb, (Object[]) RECEIPT_LIST_HEADER);
        BigDecimal docTotal = BigDecimal.ZERO;
        BigDecimal lineTotal = BigDecimal.ZERO;
        for (int i = 0; i < docs.size(); i++) {
            ReceiveDocument doc = docs.get(i);
            ReviewQueueItem item = items.get(i);
            JobCard jc = jobCards.get(doc.getJobCardId());
            Customer cust = jc == null || jc.getCustomerId() == null ? null : customers.get(jc.getCustomerId());
            Object[] head = {
                screenStatus(item.workflowStatus()), item.workflowStatus(), istDate(item.submittedAt()),
                docNo(item.documentNo(), doc.getId()), item.branchCode(), item.partyName(),
                cust == null ? null : cust.getPhone(),
                jc == null || jc.getVehicleId() == null ? null : vehicles.get(jc.getVehicleId()),
                jc == null ? null : item.branchCode() + "-JC-" + jc.getId(),
                jc == null ? null : jc.getDbmId(),
                jc == null ? null : jc.getInvoiceNo(),
                jc == null ? null : jc.getInvoiceAmount(),
                item.categoryName(), yesNo(item.isClaim())};
            docTotal = docTotal.add(item.amount());

            List<SettlementLine> lines = linesByDoc.getOrDefault(doc.getId(), List.of());
            if (lines.isEmpty()) {
                row(sb, withTail(head, RECEIPT_LIST_HEADER.length, item.amount()));
            }
            for (int j = 0; j < lines.size(); j++) {
                SettlementLine l = lines.get(j);
                boolean overridden = l.getOverriddenBy() != null;
                lineTotal = lineTotal.add(l.getAmount());
                row(sb, withTail(head, RECEIPT_LIST_HEADER.length,
                    j == 0 ? item.amount() : null,
                    l.getLineId() != null ? l.getLineId() : "L" + l.getLineNo(),
                    l.getTransactionDate(),
                    modes.getOrDefault(l.getSettlementModeId(), ""),
                    l.getBankId() == null ? null : banks.getOrDefault(l.getBankId(), ""),
                    l.getTransactionRef(),
                    l.getAmount(),
                    overridden ? l.getOriginalAmount() : null,
                    overridden ? l.getOverrideReason() : null,
                    l.getRemark()));
            }
        }
        totalRow(sb, RECEIPT_LIST_HEADER, docs.size(), docTotal, lineTotal);
        return sb.toString();
    }

    private String expenseListCsv(Long orgId, List<Long> order) {
        List<ExpenseDocument> docs = order.isEmpty() ? List.of() : inScreenOrder(order,
            expenseDocumentRepo.findByOrgIdAndIdIn(orgId, order), ExpenseDocument::getId, ExpenseDocument::getBranchId,
            d -> d.getWorkflowStatus().name());
        List<ReviewQueueItem> items = reviewService.toExpenseItems(orgId, docs);
        List<Long> docIds = docs.stream().map(ExpenseDocument::getId).toList();

        List<Long> jcIds = docs.stream().map(ExpenseDocument::getJobCardId).filter(Objects::nonNull).distinct().toList();
        Map<Long, JobCard> jobCards = jcIds.isEmpty() ? Map.of() :
            jobCardRepo.findByOrgIdAndIdIn(orgId, jcIds).stream().collect(Collectors.toMap(JobCard::getId, j -> j, (a, b) -> a));
        List<Long> customerIds = new ArrayList<>();
        List<Long> vehicleIds = new ArrayList<>();
        for (ExpenseDocument d : docs) {
            customerIds.add(d.getCustomerId());
            vehicleIds.add(d.getVehicleId());
        }
        for (JobCard jc : jobCards.values()) {
            customerIds.add(jc.getCustomerId());
            vehicleIds.add(jc.getVehicleId());
        }
        Map<Long, Customer> customers = customersById(orgId, customerIds);
        Map<Long, String> vehicles = vehicleNosById(orgId, vehicleIds);
        Map<Long, List<ExpenseLine>> linesByDoc = docIds.isEmpty() ? Map.of() :
            expenseLineRepo.findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(orgId, docIds).stream()
                .collect(Collectors.groupingBy(ExpenseLine::getExpenseDocumentId));
        Map<Long, String> modes = expenseModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(ExpenseMode::getId, ExpenseMode::getName, (a, b) -> a));
        Map<Long, String> subCategories = subCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(ExpenseSubCategory::getId, ExpenseSubCategory::getName, (a, b) -> a));
        Map<Long, String> banks = bankNames(orgId);

        StringBuilder sb = new StringBuilder();
        row(sb, (Object[]) EXPENSE_LIST_HEADER);
        BigDecimal docTotal = BigDecimal.ZERO;
        BigDecimal lineTotal = BigDecimal.ZERO;
        for (int i = 0; i < docs.size(); i++) {
            ExpenseDocument doc = docs.get(i);
            ReviewQueueItem item = items.get(i);
            JobCard jc = doc.getJobCardId() == null ? null : jobCards.get(doc.getJobCardId());
            // The document's own reference fields win; the linked job card fills the gaps.
            Long custId = doc.getCustomerId() != null ? doc.getCustomerId() : (jc == null ? null : jc.getCustomerId());
            Long vehId = doc.getVehicleId() != null ? doc.getVehicleId() : (jc == null ? null : jc.getVehicleId());
            Customer cust = custId == null ? null : customers.get(custId);
            Object[] head = {
                screenStatus(item.workflowStatus()), item.workflowStatus(), istDate(item.submittedAt()),
                docNo(item.documentNo(), doc.getId()), item.branchCode(), item.partyName(), item.categoryName(),
                firstNonBlank(doc.getCustomerName(), cust == null ? null : cust.getName()),
                firstNonBlank(doc.getVehicleNo(), vehId == null ? null : vehicles.get(vehId)),
                jc == null ? null : item.branchCode() + "-JC-" + jc.getId(),
                firstNonBlank(doc.getDbmId(), jc == null ? null : jc.getDbmId()),
                firstNonBlank(doc.getInvoiceNo(), jc == null ? null : jc.getInvoiceNo()),
                yesNo(item.overLimit()), yesNo(item.preApproved()), yesNo(item.isClaim())};
            docTotal = docTotal.add(item.amount());

            List<ExpenseLine> lines = linesByDoc.getOrDefault(doc.getId(), List.of());
            if (lines.isEmpty()) {
                row(sb, withTail(head, EXPENSE_LIST_HEADER.length, item.amount()));
            }
            for (int j = 0; j < lines.size(); j++) {
                ExpenseLine l = lines.get(j);
                boolean overridden = l.getOverriddenBy() != null;
                lineTotal = lineTotal.add(l.getAmount());
                row(sb, withTail(head, EXPENSE_LIST_HEADER.length,
                    j == 0 ? item.amount() : null,
                    l.getLineId() != null ? l.getLineId() : "L" + l.getLineNo(),
                    l.getTransactionDate(),
                    l.getSubCategoryId() == null ? null : subCategories.getOrDefault(l.getSubCategoryId(), ""),
                    modes.getOrDefault(l.getExpenseModeId(), ""),
                    l.getBankId() == null ? null : banks.getOrDefault(l.getBankId(), ""),
                    l.getTransactionRef(),
                    l.getAmount(),
                    overridden ? l.getOriginalAmount() : null,
                    overridden ? l.getOverrideReason() : null,
                    l.getRemark()));
            }
        }
        totalRow(sb, EXPENSE_LIST_HEADER, docs.size(), docTotal, lineTotal);
        return sb.toString();
    }

    private String cashListCsv(Long orgId, List<Long> order) {
        List<CashDocument> docs = order.isEmpty() ? List.of() : inScreenOrder(order,
            cashDocumentRepo.findByOrgIdAndIdIn(orgId, order), CashDocument::getId, CashDocument::getBranchId,
            d -> d.getWorkflowStatus().name());
        List<ReviewQueueItem> items = reviewService.toCashItems(orgId, docs);
        Map<Long, String> banks = bankNames(orgId);

        StringBuilder sb = new StringBuilder();
        row(sb, (Object[]) CASH_LIST_HEADER);
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < docs.size(); i++) {
            CashDocument doc = docs.get(i);
            ReviewQueueItem item = items.get(i);
            total = total.add(item.amount());
            row(sb, screenStatus(item.workflowStatus()), item.workflowStatus(), istDate(item.submittedAt()),
                docNo(item.documentNo(), doc.getId()), item.branchCode(), item.partyName(), doc.getTransactionDate(),
                doc.getBankId() == null ? null : banks.getOrDefault(doc.getBankId(), ""),
                doc.getTransactionRef(), item.amount(), doc.getRemark());
        }
        totalRow(sb, CASH_LIST_HEADER, docs.size(), total, null);
        return sb.toString();
    }

    /** The window's status chip for a workflow state; null = a state that window never lists. */
    static String screenStatus(String workflowStatus) {
        return switch (workflowStatus) {
            case "SUBMITTED", "FM_QUERIED" -> "Pending";
            case "VERIFIED" -> "Verified";
            case "APPROVED", "CLOSED" -> "Closed";
            default -> null;
        };
    }

    /** Keeps the caller's (screen) order; drops ids not found, outside the caller's branches, or in an unlisted state. */
    private <T> List<T> inScreenOrder(List<Long> order, List<T> found, Function<T, Long> id,
                                      Function<T, Long> branch, Function<T, String> status) {
        Map<Long, T> byId = found.stream()
            .filter(d -> branchScope.canSeeBranch(branch.apply(d)) && screenStatus(status.apply(d)) != null)
            .collect(Collectors.toMap(id, d -> d, (a, b) -> a));
        return order.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private Map<Long, Customer> customersById(Long orgId, List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        return distinct.isEmpty() ? Map.of() : customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId, distinct).stream()
            .collect(Collectors.toMap(Customer::getId, c -> c, (a, b) -> a));
    }

    private Map<Long, String> vehicleNosById(Long orgId, List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        return distinct.isEmpty() ? Map.of() : vehicleRepo.findByOrgIdAndIdIn(orgId, distinct).stream()
            .collect(Collectors.toMap(Vehicle::getId, Vehicle::getVehicleNo, (a, b) -> a));
    }

    private Map<Long, String> bankNames(Long orgId) {
        return bankRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(Bank::getId, Bank::getName, (a, b) -> a));
    }

    /** {@code head} followed by {@code tail}, padded with empty cells to {@code width}. */
    private static Object[] withTail(Object[] head, int width, Object... tail) {
        Object[] out = Arrays.copyOf(head, width);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }

    /**
     * Closing row: "Total" and the transaction count, with the sums under the "Transaction Amount"
     * (or cash "Amount") and "Line Amount" columns.
     */
    private static void totalRow(StringBuilder sb, String[] header, int count, BigDecimal docTotal, BigDecimal lineTotal) {
        List<String> cols = Arrays.asList(header);
        Object[] cells = new Object[header.length];
        cells[0] = "Total";
        cells[cols.indexOf("Document No")] = count + (count == 1 ? " transaction" : " transactions");
        int docCol = cols.contains("Transaction Amount") ? cols.indexOf("Transaction Amount") : cols.indexOf("Amount");
        cells[docCol] = docTotal;
        if (lineTotal != null) {
            cells[cols.indexOf("Line Amount")] = lineTotal;
        }
        row(sb, cells);
    }

    /** One CSV line: text escaped, numbers and dates plain, null as an empty cell. */
    private static void row(StringBuilder sb, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object c = cells[i];
            if (c instanceof BigDecimal n) {
                sb.append(n.toPlainString());
            } else if (c != null) {
                sb.append(escape(c.toString()));
            }
        }
        sb.append('\n');
    }

    private static String istDate(Instant at) {
        return at == null ? null : at.atZone(OrgTime.ZONE).toLocalDate().toString();
    }

    private static String docNo(String documentNo, Long id) {
        return documentNo != null ? documentNo : "#" + id;
    }

    private static String yesNo(boolean b) {
        return b ? "Yes" : "No";
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private Collection<Long> resolveBranches(Long orgId, Long requestedBranchId) {
        if (requestedBranchId != null) {
            if (!branchScope.canSeeBranch(requestedBranchId)) {
                throw DamsException.forbidden("You do not have access to branch #" + requestedBranchId);
            }
            return List.of(requestedBranchId);
        }
        return branchScope.allowedBranchIds()
            .map(set -> (Collection<Long>) set)
            .orElseGet(() -> branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream().map(Branch::getId).toList());
    }

    private static String escape(String s) {
        if (s == null) return "\"\"";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static byte[] withBom(byte[] content) {
        byte[] result = new byte[UTF8_BOM.length + content.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(content, 0, result, UTF8_BOM.length, content.length);
        return result;
    }
}
