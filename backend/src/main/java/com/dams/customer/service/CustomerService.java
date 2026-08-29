package com.dams.customer.service;

import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.customer.dto.CustomerHistoryResponse;
import com.dams.customer.dto.CustomerRequest;
import com.dams.customer.dto.CustomerResponse;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.jobcard.service.PendingAmountCalculator;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.receive.entity.ReceiveDocument;
import com.dams.receive.entity.SettlementLine;
import com.dams.receive.repository.ReceiveDocumentRepository;
import com.dams.receive.repository.SettlementLineRepository;
import com.dams.receive.service.ReceivePaymentGuard;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Customer CRUD plus the history card. Org-scoped via TenantContext; single-row reads go
 * through findByIdAndOrgId so a cross-org id can't be fetched by primary key.
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private static final int PICKER_LIMIT = 20;

    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;
    private final JobCardRepository jobCardRepo;
    private final BranchRepository branchRepo;
    private final ReceiveCategoryRepository categoryRepo;
    private final ReceiveBusinessStatusRepository statusRepo;
    private final ReceiveDocumentRepository receiveDocumentRepo;
    private final SettlementLineRepository settlementLineRepo;
    private final SettlementModeRepository settlementModeRepo;
    private final PendingAmountCalculator pendingAmountCalculator;
    private final ReceivePaymentGuard paymentGuard;

    public CustomerService(CustomerRepository customerRepo,
                           VehicleRepository vehicleRepo,
                           JobCardRepository jobCardRepo,
                           BranchRepository branchRepo,
                           ReceiveCategoryRepository categoryRepo,
                           ReceiveBusinessStatusRepository statusRepo,
                           ReceiveDocumentRepository receiveDocumentRepo,
                           SettlementLineRepository settlementLineRepo,
                           SettlementModeRepository settlementModeRepo,
                           PendingAmountCalculator pendingAmountCalculator,
                           ReceivePaymentGuard paymentGuard) {
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
        this.jobCardRepo = jobCardRepo;
        this.branchRepo = branchRepo;
        this.categoryRepo = categoryRepo;
        this.statusRepo = statusRepo;
        this.receiveDocumentRepo = receiveDocumentRepo;
        this.settlementLineRepo = settlementLineRepo;
        this.settlementModeRepo = settlementModeRepo;
        this.pendingAmountCalculator = pendingAmountCalculator;
        this.paymentGuard = paymentGuard;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> search(String q) {
        Long orgId = TenantContext.requireOrgId();
        List<Customer> customers = (q == null || q.isBlank())
            ? customerRepo.findByOrgIdOrderByNameAsc(orgId).stream().limit(PICKER_LIMIT).toList()
            : customerRepo.search(orgId, q.trim(), Limit.of(PICKER_LIMIT));

        Map<Long, List<CustomerResponse.VehicleRef>> vehiclesByCustomer = vehiclesFor(orgId,
            customers.stream().map(Customer::getId).toList());

        return customers.stream()
            .map(c -> CustomerResponse.of(c, vehiclesByCustomer.getOrDefault(c.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        Customer c = load(id);
        return CustomerResponse.of(c, vehiclesFor(c.getOrgId(), List.of(c.getId()))
            .getOrDefault(c.getId(), List.of()));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Long orgId = TenantContext.requireOrgId();
        Customer c = new Customer();
        c.setOrgId(orgId);
        c.setName(request.getName().trim());
        c.setPhone(blankToNull(request.getPhone()));
        c = customerRepo.save(c);
        log.info("Customer created: orgId={} customerId={}", orgId, c.getId());
        return CustomerResponse.of(c, List.of());
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer c = load(id);
        c.setName(request.getName().trim());
        c.setPhone(blankToNull(request.getPhone()));
        c = customerRepo.save(c);
        return CustomerResponse.of(c, vehiclesFor(c.getOrgId(), List.of(c.getId()))
            .getOrDefault(c.getId(), List.of()));
    }

    @Transactional(readOnly = true)
    public CustomerHistoryResponse history(Long id) {
        Long orgId = TenantContext.requireOrgId();
        Customer c = load(id);

        List<Vehicle> vehicles = vehicleRepo.findByOrgIdAndCustomerIdOrderByVehicleNoAsc(orgId, id);
        List<JobCard> jobCards = jobCardRepo.findByOrgIdAndCustomerIdOrderByCreatedAtDesc(orgId, id);

        Map<Long, Branch> branches = branchRepo.findByOrgIdOrderByCodeAsc(orgId).stream()
            .collect(Collectors.toMap(Branch::getId, Function.identity()));
        Map<Long, ReceiveCategory> categories = categoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(ReceiveCategory::getId, Function.identity()));
        Map<Long, ReceiveBusinessStatus> statuses = statusRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(ReceiveBusinessStatus::getId, Function.identity()));
        Map<Long, String> modeNames = settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(orgId).stream()
            .collect(Collectors.toMap(SettlementMode::getId, SettlementMode::getName));

        List<Long> jobCardIds = jobCards.stream().map(JobCard::getId).toList();

        // Every receive document for this customer's job cards, and every line under them.
        List<ReceiveDocument> docs = jobCardIds.isEmpty() ? List.of()
            : receiveDocumentRepo.findByOrgIdAndJobCardIdInOrderByCreatedAtDesc(orgId, jobCardIds);
        Map<Long, List<ReceiveDocument>> docsByJobCard = docs.stream()
            .collect(Collectors.groupingBy(ReceiveDocument::getJobCardId));
        List<Long> docIds = docs.stream().map(ReceiveDocument::getId).toList();
        List<SettlementLine> allLines = docIds.isEmpty() ? List.of()
            : settlementLineRepo.findByOrgIdAndReceiveDocumentIdInOrderByLineNoAsc(orgId, docIds);
        Map<Long, Long> jobCardByDoc = docs.stream()
            .collect(Collectors.toMap(ReceiveDocument::getId, ReceiveDocument::getJobCardId));
        Map<Long, BigDecimal> receivedByJobCard = allLines.stream().collect(Collectors.groupingBy(
            l -> jobCardByDoc.get(l.getReceiveDocumentId()),
            Collectors.reducing(BigDecimal.ZERO, SettlementLine::getAmount, BigDecimal::add)));

        List<CustomerHistoryResponse.JobCardSummary> summaries = jobCards.stream().map(j -> {
            Branch b = branches.get(j.getBranchId());
            ReceiveCategory cat = categories.get(j.getCategoryId());
            ReceiveBusinessStatus st = statuses.get(j.getBusinessStatusId());
            BigDecimal received = receivedByJobCard.getOrDefault(j.getId(), BigDecimal.ZERO);
            BigDecimal pending = pendingAmountCalculator.forJobCard(j);
            boolean claimClosed = pendingAmountCalculator.hasClaimClose(orgId, j.getId());
            List<ReceiveDocument> jcDocs = docsByJobCard.get(j.getId());
            String workflow = workflowStatusFor(jcDocs);
            ReceiveDocument primaryDoc = primaryDocFor(jcDocs);
            return new CustomerHistoryResponse.JobCardSummary(
                j.getId(),
                JobCardResponse.reference(b != null ? b.getCode() : "?", j.getId()),
                b != null ? b.getCode() : null,
                b != null ? b.getName() : null,
                cat != null ? cat.getName() : null,
                cat != null && cat.isClaim(),
                st != null ? st.getName() : null,
                j.getDbmId(),
                j.getInvoiceNo(),
                j.getInvoiceAmount(),
                received,
                pending,
                workflow,
                paymentGuard.canRecordPayment(orgId, j, pending, claimClosed),
                primaryDoc != null ? primaryDoc.getId() : null,
                primaryDoc != null && primaryDoc.isSettled(),
                j.getCreatedAt());
        }).toList();

        BigDecimal totalInvoiced = jobCards.stream()
            .map(JobCard::getInvoiceAmount).filter(a -> a != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceived = summaries.stream()
            .map(CustomerHistoryResponse.JobCardSummary::received)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutstanding = summaries.stream()
            .map(CustomerHistoryResponse.JobCardSummary::balance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CustomerHistoryResponse.TimelineEntry> timeline = buildTimeline(allLines, jobCardByDoc,
            jobCards.stream().collect(Collectors.toMap(JobCard::getId, Function.identity())),
            categories, modeNames);

        return new CustomerHistoryResponse(
            c.getId(),
            c.getName(),
            c.getPhone(),
            vehicles.stream().map(v -> new CustomerHistoryResponse.Vehicle(v.getId(), v.getVehicleNo())).toList(),
            jobCards.size(),
            totalInvoiced,
            totalReceived,
            totalOutstanding,
            summaries,
            timeline);
    }

    // --- helpers ---

    /** The open (settled = false) document; else the most recent; else null. */
    private static ReceiveDocument primaryDocFor(List<ReceiveDocument> docs) {
        if (docs == null || docs.isEmpty()) {
            return null;
        }
        return docs.stream()
            .filter(d -> !d.isSettled())
            .findFirst()
            .or(() -> docs.stream().max(Comparator.comparing(ReceiveDocument::getCreatedAt)))
            .orElse(null);
    }

    private static String workflowStatusFor(List<ReceiveDocument> docs) {
        ReceiveDocument d = primaryDocFor(docs);
        return d != null ? d.getWorkflowStatus().name() : null;
    }

    private static List<CustomerHistoryResponse.TimelineEntry> buildTimeline(
        List<SettlementLine> lines,
        Map<Long, Long> jobCardByDoc,
        Map<Long, JobCard> jobCardsById,
        Map<Long, ReceiveCategory> categories,
        Map<Long, String> modeNames) {

        return lines.stream()
            .sorted(Comparator.comparing(SettlementLine::getTransactionDate).reversed()
                .thenComparing(Comparator.comparing(SettlementLine::getCreatedAt).reversed()))
            .map(l -> {
                JobCard jc = jobCardsById.get(jobCardByDoc.get(l.getReceiveDocumentId()));
                String category = jc != null ? categoryName(categories, jc.getCategoryId()) : null;
                String desc = category != null ? category : "Payment";
                if (jc != null && jc.getDbmId() != null) {
                    desc += " - JC " + jc.getDbmId();
                }
                return new CustomerHistoryResponse.TimelineEntry(
                    l.getCreatedAt(),
                    desc,
                    l.getLineId() != null ? l.getLineId() : "L" + l.getLineNo(),
                    modeNames.get(l.getSettlementModeId()),
                    l.getAmount());
            })
            .toList();
    }

    private static String categoryName(Map<Long, ReceiveCategory> categories, Long id) {
        ReceiveCategory c = categories.get(id);
        return c != null ? c.getName() : null;
    }

    private Customer load(Long id) {
        return customerRepo.findByIdAndOrgId(id, TenantContext.requireOrgId())
            .orElseThrow(() -> DamsException.notFound("Customer", id));
    }

    private Map<Long, List<CustomerResponse.VehicleRef>> vehiclesFor(Long orgId, List<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return vehicleRepo.findByOrgIdAndCustomerIdInOrderByVehicleNoAsc(orgId, customerIds).stream()
            .collect(Collectors.groupingBy(Vehicle::getCustomerId,
                Collectors.mapping(v -> new CustomerResponse.VehicleRef(v.getId(), v.getVehicleNo()),
                    Collectors.toList())));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
