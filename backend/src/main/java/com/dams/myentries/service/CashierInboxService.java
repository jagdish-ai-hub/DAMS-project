package com.dams.myentries.service;

import com.dams.audit.entity.AuditEvent;
import com.dams.audit.entity.EventType;
import com.dams.audit.repository.AuditEventRepository;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.entity.CashWorkflowStatus;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.expense.entity.ExpenseDocument;
import com.dams.expense.entity.ExpenseLine;
import com.dams.expense.entity.ExpenseWorkflowStatus;
import com.dams.expense.entity.PreApprovalStatus;
import com.dams.expense.repository.ExpenseDocumentRepository;
import com.dams.expense.repository.ExpenseLineRepository;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.myentries.dto.CashierInboxResponse;
import com.dams.myentries.dto.CashierInboxResponse.Item;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.entity.WorkflowStatus;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receiver.entity.Receiver;
import com.dams.receiver.repository.ReceiverRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.repository.AppUserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Cashier home message boxes (rev 57). Everything is derived from each of the caller's own
 * documents' CURRENT state, so acting on one (resubmit, submit, re-request) removes it and lowers
 * the badge -- there is no read/unread bookkeeping to go stale.
 *
 * Left box  = QUERIED (Accountant, or a Finance Manager reject/query that routes to the cashier)
 *             + recently REJECTED (shown for {@value #REJECTED_DAYS} days, not counted -- nothing to act on).
 *             {@code FM_QUERIED} is the Accountant's to fix, so it is NOT here.
 * Right box = the FM pre-approval lifecycle of the caller's draft expenses: waiting, approved
 *             (submit it now), queried (fix and re-request).
 */
@Service
public class CashierInboxService {

    private static final Logger log = LoggerFactory.getLogger(CashierInboxService.class);
    private static final int REJECTED_DAYS = 7;
    private static final String RECEIPT = "ReceiveDocument";
    private static final String EXPENSE = "ExpenseDocument";
    private static final String CASH = "CashDocument";

    private final ReceiveDocumentRepository receiveRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final ExpenseDocumentRepository expenseRepo;
    private final ExpenseLineRepository expenseLineRepo;
    private final CashDocumentRepository cashRepo;
    private final JobCardRepository jobCardRepo;
    private final CustomerRepository customerRepo;
    private final ReceiverRepository receiverRepo;
    private final AuditEventRepository auditRepo;
    private final AppUserRepository userRepo;
    private final BranchScope branchScope;
    private final ObjectMapper objectMapper;

    public CashierInboxService(ReceiveDocumentRepository receiveRepo,
                               SettlementLineRepository settlementLineRepo,
                               ExpenseDocumentRepository expenseRepo,
                               ExpenseLineRepository expenseLineRepo,
                               CashDocumentRepository cashRepo,
                               JobCardRepository jobCardRepo,
                               CustomerRepository customerRepo,
                               ReceiverRepository receiverRepo,
                               AuditEventRepository auditRepo,
                               AppUserRepository userRepo,
                               BranchScope branchScope,
                               ObjectMapper objectMapper) {
        this.receiveRepo = receiveRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.expenseRepo = expenseRepo;
        this.expenseLineRepo = expenseLineRepo;
        this.cashRepo = cashRepo;
        this.jobCardRepo = jobCardRepo;
        this.customerRepo = customerRepo;
        this.receiverRepo = receiverRepo;
        this.auditRepo = auditRepo;
        this.userRepo = userRepo;
        this.branchScope = branchScope;
        this.objectMapper = objectMapper;
    }

    private record Reply(String fromName, String fromRole, String note, Instant at) {}

    @Transactional(readOnly = true)
    public CashierInboxResponse inbox() {
        Long orgId = TenantContext.requireOrgId();
        Long me = branchScope.currentUserId();
        Instant rejectedSince = Instant.now().minus(Duration.ofDays(REJECTED_DAYS));

        List<Item> queries = new ArrayList<>();
        queries.addAll(receiptQueries(orgId, me, rejectedSince));
        queries.addAll(expenseQueries(orgId, me, rejectedSince));
        queries.addAll(cashQueries(orgId, me, rejectedSince));
        // Needs-action first, then newest.
        queries.sort(Comparator.comparing(Item::needsAction).reversed()
            .thenComparing(Item::at, Comparator.nullsLast(Comparator.reverseOrder())));

        List<Item> approvals = approvalItems(orgId, me);
        approvals.sort(Comparator.comparing(Item::needsAction).reversed()
            .thenComparing(Item::at, Comparator.nullsLast(Comparator.reverseOrder())));

        int queriesToAct = (int) queries.stream().filter(Item::needsAction).count();
        int approvalsToAct = (int) approvals.stream().filter(Item::needsAction).count();
        return new CashierInboxResponse(queries, approvals, queriesToAct, approvalsToAct);
    }

    // ------------------------------------------------------------------ left box

    private List<Item> receiptQueries(Long orgId, Long me, Instant rejectedSince) {
        List<ReceiveDocument> docs = receiveRepo.findByOrgIdAndCreatedByAndWorkflowStatusIn(orgId, me,
            Set.of(WorkflowStatus.QUERIED, WorkflowStatus.REJECTED));
        if (docs.isEmpty()) {
            return List.of();
        }
        Map<Long, JobCard> jobCards = jobCardRepo.findByOrgIdAndIdIn(orgId,
                docs.stream().map(ReceiveDocument::getJobCardId).distinct().toList())
            .stream().collect(Collectors.toMap(JobCard::getId, Function.identity()));
        Map<Long, Customer> customers = customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId,
                jobCards.values().stream().map(JobCard::getCustomerId).filter(java.util.Objects::nonNull).distinct().toList())
            .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<Long, BigDecimal> totals = settlementLineRepo
            .findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(orgId, docs.stream().map(ReceiveDocument::getId).toList())
            .stream().collect(Collectors.groupingBy(SettlementLine::getReceiveDocumentId,
                Collectors.reducing(BigDecimal.ZERO, SettlementLine::getAmount, BigDecimal::add)));

        List<Item> out = new ArrayList<>();
        for (ReceiveDocument d : docs) {
            boolean rejected = d.getWorkflowStatus() == WorkflowStatus.REJECTED;
            Reply r = latestReply(orgId, RECEIPT, d.getId(), rejected ? EventType.REJECTED : EventType.QUERIED, false);
            if (rejected && (r.at() == null || r.at().isBefore(rejectedSince))) {
                continue;
            }
            JobCard jc = jobCards.get(d.getJobCardId());
            Customer c = jc != null && jc.getCustomerId() != null ? customers.get(jc.getCustomerId()) : null;
            out.add(new Item(d.getId(), "RECEIPT", d.getDocumentNo(), c != null ? c.getName() : "Receipt",
                totals.getOrDefault(d.getId(), BigDecimal.ZERO), rejected ? "REJECTED" : "QUERIED", !rejected,
                r.fromName(), r.fromRole(), r.note(), r.at()));
        }
        return out;
    }

    private List<Item> expenseQueries(Long orgId, Long me, Instant rejectedSince) {
        List<ExpenseDocument> docs = expenseRepo.findByOrgIdAndCreatedByAndWorkflowStatusIn(orgId, me,
            Set.of(ExpenseWorkflowStatus.QUERIED, ExpenseWorkflowStatus.REJECTED));
        if (docs.isEmpty()) {
            return List.of();
        }
        Map<Long, Receiver> receivers = receiverRepo.findByOrgIdAndIdIn(orgId,
                docs.stream().map(ExpenseDocument::getReceiverId).distinct().toList())
            .stream().collect(Collectors.toMap(Receiver::getId, Function.identity()));
        Map<Long, BigDecimal> totals = expenseLineRepo
            .findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(orgId, docs.stream().map(ExpenseDocument::getId).toList())
            .stream().collect(Collectors.groupingBy(ExpenseLine::getExpenseDocumentId,
                Collectors.reducing(BigDecimal.ZERO, ExpenseLine::getAmount, BigDecimal::add)));

        List<Item> out = new ArrayList<>();
        for (ExpenseDocument d : docs) {
            boolean rejected = d.getWorkflowStatus() == ExpenseWorkflowStatus.REJECTED;
            Reply r = latestReply(orgId, EXPENSE, d.getId(), rejected ? EventType.REJECTED : EventType.QUERIED, false);
            if (rejected && (r.at() == null || r.at().isBefore(rejectedSince))) {
                continue;
            }
            Receiver rc = receivers.get(d.getReceiverId());
            out.add(new Item(d.getId(), "EXPENSE", d.getDocumentNo(), rc != null ? rc.getName() : "Expense",
                totals.getOrDefault(d.getId(), BigDecimal.ZERO), rejected ? "REJECTED" : "QUERIED", !rejected,
                r.fromName(), r.fromRole(), r.note(), r.at()));
        }
        return out;
    }

    private List<Item> cashQueries(Long orgId, Long me, Instant rejectedSince) {
        List<CashDocument> docs = cashRepo.findByOrgIdAndCreatedByAndWorkflowStatusIn(orgId, me,
            Set.of(CashWorkflowStatus.QUERIED, CashWorkflowStatus.REJECTED));
        List<Item> out = new ArrayList<>();
        for (CashDocument d : docs) {
            boolean rejected = d.getWorkflowStatus() == CashWorkflowStatus.REJECTED;
            Reply r = latestReply(orgId, CASH, d.getId(), rejected ? EventType.REJECTED : EventType.QUERIED, false);
            if (rejected && (r.at() == null || r.at().isBefore(rejectedSince))) {
                continue;
            }
            String label = d.getDirection().name().equals("IN") ? "Cash IN from bank" : "Cash OUT to bank";
            out.add(new Item(d.getId(), "CASH", d.getDocumentNo(), label, d.getAmount(),
                rejected ? "REJECTED" : "QUERIED", !rejected, r.fromName(), r.fromRole(), r.note(), r.at()));
        }
        return out;
    }

    // ------------------------------------------------------------------ right box

    private List<Item> approvalItems(Long orgId, Long me) {
        List<ExpenseDocument> docs = expenseRepo.findByOrgIdAndCreatedByAndWorkflowStatusAndPreApprovalStatusIsNotNull(
            orgId, me, ExpenseWorkflowStatus.DRAFT);
        if (docs.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Receiver> receivers = receiverRepo.findByOrgIdAndIdIn(orgId,
                docs.stream().map(ExpenseDocument::getReceiverId).distinct().toList())
            .stream().collect(Collectors.toMap(Receiver::getId, Function.identity()));
        Map<Long, BigDecimal> totals = expenseLineRepo
            .findByOrgIdAndExpenseDocumentIdInOrderByLineNoAsc(orgId, docs.stream().map(ExpenseDocument::getId).toList())
            .stream().collect(Collectors.groupingBy(ExpenseLine::getExpenseDocumentId,
                Collectors.reducing(BigDecimal.ZERO, ExpenseLine::getAmount, BigDecimal::add)));

        List<Item> out = new ArrayList<>();
        for (ExpenseDocument d : docs) {
            PreApprovalStatus st = d.getPreApprovalStatus();
            Receiver rc = receivers.get(d.getReceiverId());
            String title = rc != null ? rc.getName() : "Expense";
            BigDecimal total = totals.getOrDefault(d.getId(), BigDecimal.ZERO);
            // Drafts have no document number yet; the id identifies the request.
            String no = d.getDocumentNo();
            switch (st) {
                case PENDING -> out.add(new Item(d.getId(), "EXPENSE", no, title, total, "PRE_PENDING", false,
                    null, null, "Waiting for the Finance Manager", d.getApprovalRequestedAt()));
                case APPROVED -> {
                    Reply r = latestReply(orgId, EXPENSE, d.getId(), EventType.PRE_APPROVED, null);
                    String note = "Approved" + (d.getPreApprovedAmount() != null
                        ? " up to ₹" + d.getPreApprovedAmount().stripTrailingZeros().toPlainString() : "")
                        + " — you can submit it now";
                    out.add(new Item(d.getId(), "EXPENSE", no, title, total, "PRE_APPROVED", true,
                        r.fromName(), r.fromRole(), note, r.at() != null ? r.at() : d.getPreApprovedAt()));
                }
                case QUERIED -> {
                    Reply r = latestReply(orgId, EXPENSE, d.getId(), EventType.QUERIED, true);
                    out.add(new Item(d.getId(), "EXPENSE", no, title, total, "PRE_QUERIED", true,
                        r.fromName(), r.fromRole(), r.note(), r.at()));
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ audit lookup

    /**
     * The newest audit event of {@code type} on the document -- who wrote it, in what role, the
     * note text and when. {@code preApproval} narrows QUERIED events to the FM approval-request
     * query (true) or the ordinary review query (false); null = don't care.
     */
    private Reply latestReply(Long orgId, String entityType, Long docId, EventType type, Boolean preApproval) {
        for (AuditEvent e : auditRepo.findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(orgId, entityType, docId)) {
            if (e.getEventType() != type) {
                continue;
            }
            Map<String, Object> d = detail(e);
            if (preApproval != null && Boolean.TRUE.equals(d.get("preApproval")) != preApproval) {
                continue;
            }
            String note = null;
            for (String key : List.of("note", "reason", "summary")) {
                if (d.get(key) instanceof String s && !s.isBlank()) {
                    note = s;
                    break;
                }
            }
            AppUser actor = e.getActorId() == null ? null : userRepo.findById(e.getActorId()).orElse(null);
            String role = e.getActorRole() != null ? e.getActorRole()
                : (actor != null && actor.getRole() != null ? actor.getRole().name() : null);
            return new Reply(actor != null ? actor.getName() : null, role, note, e.getCreatedAt());
        }
        return new Reply(null, null, null, null);
    }

    private Map<String, Object> detail(AuditEvent e) {
        if (e.getDetail() == null || e.getDetail().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(e.getDetail(), new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Could not parse audit detail for event #{}: {}", e.getId(), ex.getMessage());
            return Map.of();
        }
    }
}
