package com.dams.myentries.service;

import com.dams.common.security.BranchScope;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
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
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "My Entries" — the documents the signed-in user created, newest first, so a cashier can
 * see today's work and catch queried items. Read-only; scoped to {@code created_by = me}.
 */
@Service
public class MyEntriesService {

    private static final int RECENT_LIMIT = 50;

    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final JobCardRepository jobCardRepo;
    private final CustomerRepository customerRepo;
    private final BranchRepository branchRepo;
    private final PendingAmountCalculator pendingAmountCalculator;
    private final BranchScope branchScope;

    public MyEntriesService(ReceiveDocumentRepository receiveDocumentRepo,
                            SettlementLineRepository settlementLineRepo,
                            JobCardRepository jobCardRepo,
                            CustomerRepository customerRepo,
                            BranchRepository branchRepo,
                            PendingAmountCalculator pendingAmountCalculator,
                            BranchScope branchScope) {
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.jobCardRepo = jobCardRepo;
        this.customerRepo = customerRepo;
        this.branchRepo = branchRepo;
        this.pendingAmountCalculator = pendingAmountCalculator;
        this.branchScope = branchScope;
    }

    @Transactional(readOnly = true)
    public List<MyEntryResponse> list() {
        Long orgId = TenantContext.requireOrgId();
        Long userId = branchScope.currentUserId();
        LocalDate today = OrgTime.today();

        List<ReceiveDocument> docs = receiveDocumentRepo
            .findByOrgIdAndCreatedByOrderByCreatedAtDesc(orgId, userId, Limit.of(RECENT_LIMIT));
        if (docs.isEmpty()) {
            return List.of();
        }

        List<Long> jobCardIds = docs.stream().map(ReceiveDocument::getJobCardId).distinct().toList();
        Map<Long, JobCard> jobCards = loadJobCards(orgId, jobCardIds);
        List<Long> customerIds = jobCards.values().stream().map(JobCard::getCustomerId).distinct().toList();
        Map<Long, Customer> customers = customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId, customerIds).stream()
            .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<Long, Branch> branches = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, Function.identity()));

        List<Long> docIds = docs.stream().map(ReceiveDocument::getId).toList();
        Map<Long, List<SettlementLine>> linesByDoc = settlementLineRepo
            .findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(orgId, docIds).stream()
            .collect(Collectors.groupingBy(SettlementLine::getReceiveDocumentId));

        return docs.stream().map(doc -> {
            JobCard jc = jobCards.get(doc.getJobCardId());
            Customer customer = jc != null ? customers.get(jc.getCustomerId()) : null;
            Branch branch = branches.get(doc.getBranchId());
            List<SettlementLine> lines = linesByDoc.getOrDefault(doc.getId(), List.of());
            BigDecimal totalReceived = lines.stream()
                .map(SettlementLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pending = jc != null ? pendingAmountCalculator.forJobCard(jc) : BigDecimal.ZERO;
            boolean isToday = doc.getCreatedAt().atZone(OrgTime.ZONE).toLocalDate().isEqual(today);

            return new MyEntryResponse(
                doc.getId(),
                "RECEIPT",
                doc.getDocumentNo(),
                doc.getWorkflowStatus().name(),
                doc.isSettled(),
                doc.getJobCardId(),
                JobCardResponse.reference(branch != null ? branch.getCode() : "?", doc.getJobCardId()),
                customer != null ? customer.getName() : null,
                totalReceived,
                pending,
                lines.size(),
                isToday,
                doc.getWorkflowStatus() == WorkflowStatus.QUERIED,
                doc.getCreatedAt(),
                doc.getSubmittedAt());
        }).toList();
    }

    private Map<Long, JobCard> loadJobCards(Long orgId, List<Long> ids) {
        return ids.stream()
            .map(id -> jobCardRepo.findByIdAndOrgId(id, orgId).orElse(null))
            .filter(jc -> jc != null)
            .collect(Collectors.toMap(JobCard::getId, Function.identity()));
    }
}
