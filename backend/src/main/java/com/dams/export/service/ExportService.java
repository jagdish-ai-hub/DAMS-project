package com.dams.export.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.masters.entity.Bank;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.ExpenseSubCategory;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.*;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
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
                         BranchScope branchScope) {
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
    }

    @Transactional(readOnly = true)
    public byte[] exportReceiptsCsv(Long requestedBranchId, LocalDate fromDate, LocalDate toDate) {
        Long orgId = TenantContext.requireOrgId();
        Collection<Long> branches = resolveBranches(orgId, requestedBranchId);
        LocalDate from = fromDate != null ? fromDate : OrgTime.today().minusDays(90);
        LocalDate to = toDate != null ? toDate : OrgTime.today();

        List<Object[]> rows = settlementLineRepo.findLinesForExport(orgId, branches, from, to);

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
            Customer cust = customers.get(jc.getCustomerId());
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
        LocalDate from = fromDate != null ? fromDate : OrgTime.today().minusDays(90);
        LocalDate to = toDate != null ? toDate : OrgTime.today();

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
