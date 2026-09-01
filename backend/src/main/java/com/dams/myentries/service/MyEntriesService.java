package com.dams.myentries.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.entity.CashWorkflowStatus;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.common.security.BranchScope;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.myentries.dto.MyEntryResponse;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "My Entries" — the documents the signed-in user created (receipts and expenses), newest
 * first, so a cashier can see today's work and catch queried items. Read-only; scoped to
 * {@code created_by = me}.
 */
@Service
public class MyEntriesService {

    private static final int RECENT_LIMIT = 50;

    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ExpenseDocumentRepository expenseDocumentRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final CashDocumentRepository cashDocumentRepo;
    private final JobCardRepository jobCardRepo;
    private final CustomerRepository customerRepo;
    private final ReceiverRepository receiverRepo;
    private final BranchRepository branchRepo;
    private final PendingAmountCalculator pendingAmountCalculator;
    private final BranchScope branchScope;

    public MyEntriesService(ReceiveDocumentRepository receiveDocumentRepo,
                            SettlementLineRepository settlementLineRepo,
                            ExpenseDocumentRepository expenseDocumentRepo,
                            ExpenseLineRepository expenseLineRepo,
                            CashDocumentRepository cashDocumentRepo,
                            JobCardRepository jobCardRepo,
                            CustomerRepository customerRepo,
                            ReceiverRepository receiverRepo,
                            BranchRepository branchRepo,
                            PendingAmountCalculator pendingAmountCalculator,
                            BranchScope branchScope) {
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.expenseDocumentRepo = expenseDocumentRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.cashDocumentRepo = cashDocumentRepo;
        this.jobCardRepo = jobCardRepo;
        this.customerRepo = customerRepo;
        this.receiverRepo = receiverRepo;
        this.branchRepo = branchRepo;
        this.pendingAmountCalculator = pendingAmountCalculator;
        this.branchScope = branchScope;
    }

    @Transactional(readOnly = true)
    public List<MyEntryResponse> list() {
        Long orgId = TenantContext.requireOrgId();
        Long userId = branchScope.currentUserId();
        LocalDate today = OrgTime.today();

        Map<Long, Branch> branches = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, Function.identity()));

        List<MyEntryResponse> rows = new ArrayList<>();
        rows.addAll(receiptRows(orgId, userId, today, branches));
        rows.addAll(expenseRows(orgId, userId, today, branches));
        rows.addAll(cashRows(orgId, userId, today, branches));

        rows.sort(Comparator.comparing(MyEntryResponse::createdAt).reversed());
        return rows.size() > RECENT_LIMIT ? rows.subList(0, RECENT_LIMIT) : rows;
    }

    // --- receipts ---

    private List<MyEntryResponse> receiptRows(Long orgId, Long userId, LocalDate today, Map<Long, Branch> branches) {
        List<ReceiveDocument> docs = receiveDocumentRepo
            .findByOrgIdAndCreatedByOrderByCreatedAtDesc(orgId, userId, Limit.of(RECENT_LIMIT));
        if (docs.isEmpty()) {
            return List.of();
        }
        List<Long> jobCardIds = docs.stream().map(ReceiveDocument::getJobCardId).distinct().toList();
        Map<Long, JobCard> jobCards = loadJobCards(orgId, jobCardIds);
        Map<Long, BigDecimal> pendingByJc = pendingAmountCalculator.forJobCards(orgId, jobCards.values());
        List<Long> customerIds = jobCards.values().stream().map(JobCard::getCustomerId).distinct().toList();
        Map<Long, Customer> customers = customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId, customerIds).stream()
            .collect(Collectors.toMap(Customer::getId, Function.identity()));

        List<Long> docIds = docs.stream().map(ReceiveDocument::getId).toList();
        Map<Long, List<SettlementLine>> linesByDoc = settlementLineRepo
            .findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(orgId, docIds).stream()
            .collect(Collectors.groupingBy(SettlementLine::getReceiveDocumentId));

        return docs.stream().map(doc -> {
            JobCard jc = jobCards.get(doc.getJobCardId());
            Customer customer = jc != null ? customers.get(jc.getCustomerId()) : null;
            Branch branch = branches.get(doc.getBranchId());
            List<SettlementLine> lines = linesByDoc.getOrDefault(doc.getId(), List.of());
            BigDecimal total = lines.stream().map(SettlementLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pending = jc != null ? pendingByJc.getOrDefault(jc.getId(), BigDecimal.ZERO) : BigDecimal.ZERO;

            return new MyEntryResponse(
                doc.getId(),
                "RECEIPT",
                doc.getDocumentNo(),
                doc.getWorkflowStatus().name(),
                doc.isSettled(),
                doc.getJobCardId(),
                JobCardResponse.reference(branch != null ? branch.getCode() : "?", doc.getJobCardId()),
                customer != null ? customer.getName() : null,
                total,
                pending,
                lines.size(),
                false,
                isToday(doc.getCreatedAt(), today),
                doc.getWorkflowStatus() == WorkflowStatus.QUERIED,
                doc.getCreatedAt(),
                doc.getSubmittedAt());
        }).toList();
    }

    // --- expenses ---

    private List<MyEntryResponse> expenseRows(Long orgId, Long userId, LocalDate today, Map<Long, Branch> branches) {
        List<ExpenseDocument> docs = expenseDocumentRepo
            .findByOrgIdAndCreatedByOrderByCreatedAtDesc(orgId, userId, Limit.of(RECENT_LIMIT));
        if (docs.isEmpty()) {
            return List.of();
        }
        List<Long> receiverIds = docs.stream().map(ExpenseDocument::getReceiverId).distinct().toList();
        Map<Long, Receiver> receivers = receiverIds.isEmpty() ? Map.of()
            : receiverRepo.findByOrgIdAndIdIn(orgId, receiverIds).stream()
                .collect(Collectors.toMap(Receiver::getId, Function.identity()));

        List<Long> docIds = docs.stream().map(ExpenseDocument::getId).toList();
        Map<Long, List<ExpenseLine>> linesByDoc = expenseLineRepo
            .findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(orgId, docIds).stream()
            .collect(Collectors.groupingBy(ExpenseLine::getExpenseDocumentId));

        return docs.stream().map(doc -> {
            Branch branch = branches.get(doc.getBranchId());
            Receiver receiver = receivers.get(doc.getReceiverId());
            List<ExpenseLine> lines = linesByDoc.getOrDefault(doc.getId(), List.of());
            BigDecimal total = lines.stream().map(ExpenseLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            return new MyEntryResponse(
                doc.getId(),
                "EXPENSE",
                doc.getDocumentNo(),
                doc.getWorkflowStatus().name(),
                false,
                doc.getJobCardId(),
                doc.getJobCardId() == null ? null
                    : JobCardResponse.reference(branch != null ? branch.getCode() : "?", doc.getJobCardId()),
                receiver != null ? receiver.getName() : null,
                total,
                null,
                lines.size(),
                doc.isOverLimit(),
                isToday(doc.getCreatedAt(), today),
                doc.getWorkflowStatus() == ExpenseWorkflowStatus.QUERIED,
                doc.getCreatedAt(),
                doc.getSubmittedAt());
        }).toList();
    }

    // --- cash movements ---

    private List<MyEntryResponse> cashRows(Long orgId, Long userId, LocalDate today, Map<Long, Branch> branches) {
        List<CashDocument> docs = cashDocumentRepo
            .findByOrgIdAndCreatedByOrderByCreatedAtDesc(orgId, userId, Limit.of(RECENT_LIMIT));
        return docs.stream().map(doc -> {
            String label = doc.getDirection().name().equals("IN") ? "Cash IN from bank" : "Cash OUT to bank";
            return new MyEntryResponse(
                doc.getId(),
                "CASH",
                doc.getDocumentNo(),
                doc.getWorkflowStatus().name(),
                false,
                null,
                null,
                label,
                doc.getAmount(),
                null,
                1,
                false,
                isToday(doc.getCreatedAt(), today),
                doc.getWorkflowStatus() == CashWorkflowStatus.QUERIED,
                doc.getCreatedAt(),
                doc.getSubmittedAt());
        }).toList();
    }

    private boolean isToday(Instant createdAt, LocalDate today) {
        return createdAt.atZone(OrgTime.ZONE).toLocalDate().isEqual(today);
    }

    private Map<Long, JobCard> loadJobCards(Long orgId, List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jobCardRepo.findByOrgIdAndIdIn(orgId, ids).stream()
            .collect(Collectors.toMap(JobCard::getId, Function.identity()));
    }
}
