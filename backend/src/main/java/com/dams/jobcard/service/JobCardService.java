package com.dams.jobcard.service;

import com.dams.common.security.ActingDetails;
import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.customer.service.PartyResolver;
import com.dams.jobcard.dto.AttachCustomerRequest;
import com.dams.jobcard.dto.JobCardSearchHit;
import com.dams.jobcard.dto.JobCardCreateRequest;
import com.dams.jobcard.dto.JobCardPatchRequest;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.receive.service.ReceivePaymentGuard;
import com.dams.masters.entity.ClaimType;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.repository.ClaimTypeRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.service.ReceiveStatusAccessService;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Job-card (case) lifecycle for Stage 3: create (with inline customer/vehicle), read with
 * derived fields, and PATCH.
 *
 * PATCH rules (plan.md): invoice_no / invoice_amount / dbm_id are editable at any time;
 * category_id / claim_type_id are editable only while the job card has no ClaimClose row. A
 * category change writes a CATEGORY_CHANGED audit event with the before/after ids.
 *
 * business_status_id is the exception to that freeze: the Finance Manager can still change
 * it after a claim is closed, because their half of the status list (Closed / Claim
 * Received / Claim Pending) describes what happened to the claim money, which is only known
 * once the claim is settled. Everyone else is frozen out at close as before.
 *
 * Which statuses a given role may set at all is org data, not code — see
 * {@link ReceiveStatusAccessService}.
 */
@Service
public class JobCardService {

    private static final Logger log = LoggerFactory.getLogger(JobCardService.class);
    private static final String ENTITY = "JobCard";

    private final JobCardRepository jobCardRepo;
    private final CustomerRepository customerRepo;
    private final VehicleRepository vehicleRepo;
    private final BranchRepository branchRepo;
    private final ReceiveCategoryRepository categoryRepo;
    private final ReceiveBusinessStatusRepository statusRepo;
    private final ClaimTypeRepository claimTypeRepo;
    private final AppUserRepository userRepo;
    private final BranchScope branchScope;
    private final AuditService auditService;
    private final PendingAmountCalculator pendingAmountCalculator;
    private final ClaimCloseRepository claimCloseRepo;
    private final ReceivePaymentGuard paymentGuard;
    private final ReceiveStatusAccessService statusAccess;
    private final PartyResolver partyResolver;

    public JobCardService(JobCardRepository jobCardRepo,
                          CustomerRepository customerRepo,
                          VehicleRepository vehicleRepo,
                          BranchRepository branchRepo,
                          ReceiveCategoryRepository categoryRepo,
                          ReceiveBusinessStatusRepository statusRepo,
                          ClaimTypeRepository claimTypeRepo,
                          AppUserRepository userRepo,
                          BranchScope branchScope,
                          AuditService auditService,
                          PendingAmountCalculator pendingAmountCalculator,
                          ClaimCloseRepository claimCloseRepo,
                          ReceivePaymentGuard paymentGuard,
                          ReceiveStatusAccessService statusAccess,
                          PartyResolver partyResolver) {
        this.jobCardRepo = jobCardRepo;
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
        this.branchRepo = branchRepo;
        this.categoryRepo = categoryRepo;
        this.statusRepo = statusRepo;
        this.claimTypeRepo = claimTypeRepo;
        this.userRepo = userRepo;
        this.branchScope = branchScope;
        this.auditService = auditService;
        this.pendingAmountCalculator = pendingAmountCalculator;
        this.claimCloseRepo = claimCloseRepo;
        this.paymentGuard = paymentGuard;
        this.statusAccess = statusAccess;
        this.partyResolver = partyResolver;
    }

    @Transactional(readOnly = true)
    public JobCardResponse get(Long id) {
        return toResponse(loadVisible(id));
    }

    @Transactional
    public JobCardResponse create(JobCardCreateRequest request) {
        Long orgId = TenantContext.requireOrgId();

        Long branchId = resolvePostingBranch(orgId, request.getBranchId());
        // Customer is optional (rev 56): an Expense may open a job card before the customer is known.
        PartyResolver.Party party = partyResolver.resolve(orgId, request.getCustomerId(), request.getCustomerName(),
            request.getCustomerPhone(), request.getVehicleId(), request.getVehicleNo(), branchId);
        Customer customer = party.customer();
        Vehicle vehicle = party.vehicle();

        ReceiveCategory category = requireActiveCategory(orgId, request.getCategoryId());
        ReceiveBusinessStatus status = requireActiveStatus(orgId, request.getBusinessStatusId());
        statusAccess.requireMaySet(orgId, branchScope.currentRole(), status);
        ClaimType claimType = request.getClaimTypeId() == null ? null
            : requireActiveClaimType(orgId, request.getClaimTypeId());

        JobCard jc = new JobCard();
        jc.setOrgId(orgId);
        jc.setBranchId(branchId);
        jc.setCustomerId(customer != null ? customer.getId() : null);
        jc.setVehicleId(vehicle != null ? vehicle.getId() : null);
        jc.setVehicleNoText(party.unlinkedVehicleNo());
        jc.setDbmId(blankToNull(request.getDbmId()));
        jc.setInvoiceNo(blankToNull(request.getInvoiceNo()));
        jc.setInvoiceAmount(request.getInvoiceAmount());
        boolean b2b = Boolean.TRUE.equals(request.getB2b());
        jc.setB2b(b2b);
        jc.setGstNo(blankToNull(request.getGstNo()));
        requireGstWhenB2b(b2b, jc.getGstNo());
        jc.setCategoryId(category.getId());
        jc.setClaimTypeId(claimType != null ? claimType.getId() : null);
        jc.setBusinessStatusId(status.getId());
        jc = jobCardRepo.save(jc);

        Map<String, Object> createdDetail = orderedDetail("customerId", customer != null ? customer.getId() : null,
            "branchId", branchId);
        createdDetail.put("categoryId", category.getId());
        auditService.recordUserEvent(ENTITY, jc.getId(), jc.getBranchId(), EventType.CREATED, branchScope.currentUserId(),
            createdDetail);

        log.info("JobCard created: orgId={} jobCardId={} branchId={} customerId={}",
            orgId, jc.getId(), branchId, customer != null ? customer.getId() : null);
        return toResponse(jc);
    }

    @Transactional
    public JobCardResponse patch(Long id, JobCardPatchRequest request) {
        Long orgId = TenantContext.requireOrgId();
        JobCard jc = loadVisible(id);

        // Free-to-edit references
        if (request.getInvoiceNo() != null) {
            jc.setInvoiceNo(blankToNull(request.getInvoiceNo()));
        }
        if (Boolean.TRUE.equals(request.getClearInvoiceAmount())) {
            jc.setInvoiceAmount(null);
        } else if (request.getInvoiceAmount() != null) {
            jc.setInvoiceAmount(request.getInvoiceAmount());
        }
        if (request.getDbmId() != null) {
            jc.setDbmId(blankToNull(request.getDbmId()));
        }
        if (request.getB2b() != null) {
            jc.setB2b(request.getB2b());
        }
        if (request.getGstNo() != null) {
            jc.setGstNo(blankToNull(request.getGstNo()));
        }
        if (request.getVehicleNo() != null) {
            String normalised = Vehicle.normalise(request.getVehicleNo());
            if (normalised == null || normalised.isBlank()) {
                jc.setVehicleId(null);
                jc.setVehicleNoText(null);
            } else if (jc.getCustomerId() == null) {
                // No customer to own a Vehicle row yet -- keep the number as text (rev 56).
                jc.setVehicleId(null);
                jc.setVehicleNoText(normalised);
            } else {
                PartyResolver.Party party = partyResolver.resolve(orgId, jc.getCustomerId(), null, null,
                    null, normalised, jc.getBranchId());
                jc.setVehicleId(party.vehicle().getId());
                jc.setVehicleNoText(null);
            }
        }
        // Validate against the effective (post-patch) values.
        requireGstWhenB2b(jc.isB2b(), jc.getGstNo());

        boolean wantsCategoryChange = request.getCategoryId() != null
            && !request.getCategoryId().equals(jc.getCategoryId());
        boolean wantsStatusChange = request.getBusinessStatusId() != null
            && !request.getBusinessStatusId().equals(jc.getBusinessStatusId());
        // 0 means "clear it"; any other non-null id is a real claim type. Either way, only
        // an actual change against the current value counts.
        boolean wantsClaimTypeChange = request.getClaimTypeId() != null
            && !request.getClaimTypeId().equals(jc.getClaimTypeId() != null ? jc.getClaimTypeId() : 0L);

        boolean claimClosed = hasClaimClose(jc.getId());
        if ((wantsCategoryChange || wantsClaimTypeChange) && claimClosed) {
            throw DamsException.conflict(
                "This job card's claim is closed — category and claim type can no longer be changed");
        }
        // Finance keeps the status editable after close: Closed / Claim Received / Claim
        // Pending record how the claim settled, which nobody knows until after closing.
        if (wantsStatusChange && claimClosed && branchScope.currentRole() != Role.FINANCE_MANAGER) {
            throw DamsException.conflict(
                "This job card's claim is closed — only a Finance Manager can change its business status now");
        }

        if (wantsCategoryChange) {
            Long before = jc.getCategoryId();
            ReceiveCategory next = requireActiveCategory(orgId, request.getCategoryId());
            jc.setCategoryId(next.getId());
            auditService.recordUserEvent(ENTITY, jc.getId(), jc.getBranchId(), EventType.CATEGORY_CHANGED,
                branchScope.currentUserId(),
                orderedDetail("before", before, "after", next.getId()));
        }
        if (wantsClaimTypeChange) {
            Long before = jc.getClaimTypeId();
            Long after;
            if (request.getClaimTypeId() == 0L) {
                after = null;
            } else {
                ClaimType next = requireActiveClaimType(orgId, request.getClaimTypeId());
                after = next.getId();
            }
            jc.setClaimTypeId(after);
            auditService.recordUserEvent(ENTITY, jc.getId(), jc.getBranchId(), EventType.CLAIM_TYPE_CHANGED,
                branchScope.currentUserId(),
                orderedDetail("before", before, "after", after));
        }
        if (wantsStatusChange) {
            ReceiveBusinessStatus next = requireActiveStatus(orgId, request.getBusinessStatusId());
            statusAccess.requireMaySet(orgId, branchScope.currentRole(), next);
            jc.setBusinessStatusId(next.getId());
        }

        jc = jobCardRepo.save(jc);
        log.info("JobCard patched: orgId={} jobCardId={} categoryChanged={} claimTypeChanged={} statusChanged={}",
            orgId, jc.getId(), wantsCategoryChange, wantsClaimTypeChange, wantsStatusChange);
        return toResponse(jc);
    }

    // --- resolution helpers ---

    /**
     * CASHIER: always their home branch (the request's branchId is ignored).
     * Everyone else: the requested branch, which must exist and be within their branch scope.
     */
    private Long resolvePostingBranch(Long orgId, Long requestedBranchId) {
        AppUser me = userRepo.findByIdAndOrganization_Id(branchScope.currentUserId(), orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));

        if (ActingDetails.effectiveRole(me) == Role.CASHIER) {
            if (ActingDetails.effectiveHomeBranch(me) == null) {
                throw DamsException.badRequest("Your account has no home branch — ask an Owner to set one");
            }
            return ActingDetails.effectiveHomeBranch(me);
        }

        if (requestedBranchId == null) {
            throw DamsException.badRequest("branchId is required");
        }
        Branch branch = branchRepo.findByIdAndOrgId(requestedBranchId, orgId)
            .orElseThrow(() -> DamsException.notFound("Branch", requestedBranchId));
        if (!branch.isActive()) {
            throw DamsException.badRequest("Branch '" + branch.getCode() + "' is inactive");
        }
        if (!branchScope.canSeeBranch(branch.getId())) {
            throw DamsException.forbidden("You do not have access to branch '" + branch.getCode() + "'");
        }
        return branch.getId();
    }

    private ReceiveCategory requireActiveCategory(Long orgId, Long categoryId) {
        ReceiveCategory c = categoryRepo.findByIdAndOrgId(categoryId, orgId)
            .orElseThrow(() -> DamsException.notFound("Receive category", categoryId));
        if (!c.isActive()) {
            throw DamsException.badRequest("Receive category '" + c.getName() + "' is inactive");
        }
        return c;
    }

    private ReceiveBusinessStatus requireActiveStatus(Long orgId, Long statusId) {
        ReceiveBusinessStatus s = statusRepo.findByIdAndOrgId(statusId, orgId)
            .orElseThrow(() -> DamsException.notFound("Receive business status", statusId));
        if (!s.isActive()) {
            throw DamsException.badRequest("Business status '" + s.getName() + "' is inactive");
        }
        return s;
    }

    private ClaimType requireActiveClaimType(Long orgId, Long claimTypeId) {
        ClaimType c = claimTypeRepo.findByIdAndOrgId(claimTypeId, orgId)
            .orElseThrow(() -> DamsException.notFound("Claim type", claimTypeId));
        if (!c.isActive()) {
            throw DamsException.badRequest("Claim type '" + c.getName() + "' is inactive");
        }
        return c;
    }

    /** Whether an immutable ClaimClose exists for this job card (freezes category / status). */
    private boolean hasClaimClose(Long jobCardId) {
        return claimCloseRepo.existsByOrgIdAndJobCardId(TenantContext.requireOrgId(), jobCardId);
    }

    private static void requireGstWhenB2b(boolean b2b, String gstNo) {
        if (b2b && (gstNo == null || gstNo.isBlank())) {
            throw DamsException.badRequest("GST number is required for a B2B job card");
        }
    }

    // --- read model ---

    private JobCard load(Long id) {
        return jobCardRepo.findByIdAndOrgId(id, TenantContext.requireOrgId())
            .orElseThrow(() -> DamsException.notFound("Job card", id));
    }

    /**
     * load() plus the branch gate: with the cashier toggle OFF a cashier (or a
     * branch-scoped accountant) must not read or mutate another branch's job card.
     */
    private JobCard loadVisible(Long id) {
        JobCard jc = load(id);
        if (!branchScope.canSeeBranch(jc.getBranchId())) {
            throw DamsException.forbidden(
                "Job card " + id + " is in branch " + jc.getBranchId() + ", which is outside your access");
        }
        return jc;
    }

    private JobCardResponse toResponse(JobCard jc) {
        Long orgId = jc.getOrgId();
        Branch branch = branchRepo.findByIdAndOrgId(jc.getBranchId(), orgId).orElse(null);
        Customer customer = jc.getCustomerId() == null ? null
            : customerRepo.findByIdAndOrgId(jc.getCustomerId(), orgId).orElse(null);
        Vehicle vehicle = jc.getVehicleId() == null ? null
            : vehicleRepo.findByIdAndOrgId(jc.getVehicleId(), orgId).orElse(null);
        ReceiveCategory category = categoryRepo.findByIdAndOrgId(jc.getCategoryId(), orgId).orElse(null);
        ClaimType claimType = jc.getClaimTypeId() == null ? null
            : claimTypeRepo.findByIdAndOrgId(jc.getClaimTypeId(), orgId).orElse(null);
        ReceiveBusinessStatus status = statusRepo.findByIdAndOrgId(jc.getBusinessStatusId(), orgId).orElse(null);

        String branchCode = branch != null ? branch.getCode() : "?";

        ClaimClose claimClose = claimCloseRepo.findByOrgIdAndJobCardId(orgId, jc.getId()).orElse(null);
        BigDecimal pending = pendingAmountCalculator.forJobCard(jc);
        boolean claimClosed = claimClose != null;
        String claimClosedByName = claimClose == null ? null
            : userRepo.findById(claimClose.getClosedBy()).map(AppUser::getName).orElse(null);

        return new JobCardResponse(
            jc.getId(),
            JobCardResponse.reference(branchCode, jc.getId()),
            jc.getBranchId(),
            branch != null ? branch.getCode() : null,
            branch != null ? branch.getName() : null,
            jc.getCustomerId(),
            customer != null ? customer.getName() : null,
            customer != null ? customer.getPhone() : null,
            jc.getVehicleId(),
            vehicle != null ? vehicle.getVehicleNo() : jc.getVehicleNoText(),
            jc.getDbmId(),
            jc.getInvoiceNo(),
            jc.getInvoiceAmount(),
            jc.isB2b(),
            jc.getGstNo(),
            jc.getCategoryId(),
            category != null ? category.getName() : null,
            jc.getClaimTypeId(),
            claimType != null ? claimType.getName() : null,
            jc.getClaimTypeId() != null,
            jc.getBusinessStatusId(),
            status != null ? status.getName() : null,
            pending,
            claimClosed,
            claimClose != null ? claimClose.getFinalAmount() : null,
            claimClose != null && claimClose.isOverridden(),
            claimClose != null ? claimClose.getOverrideReason() : null,
            claimClosedByName,
            claimClose != null ? claimClose.getClosedAt() : null,
            paymentGuard.canRecordPayment(orgId, jc, pending, claimClosed),
            jc.getCreatedAt());
    }

    // --- attach customer / search (rev 56) ---

    /**
     * Attach the customer to a job card that was opened without one (from an Expense). Set once:
     * a Cashier of the job card's branch, only while the customer is empty. A typed-only vehicle
     * number becomes a real Vehicle under that customer. Correcting an attached customer is not
     * built yet (it needs a defined vehicle re-assign rule) -- it is refused, not silently allowed.
     */
    @Transactional
    public JobCardResponse attachCustomer(Long id, AttachCustomerRequest request) {
        Long orgId = TenantContext.requireOrgId();
        JobCard jc = loadVisible(id);
        attachCustomerInternal(orgId, jc, request.getCustomerId(), request.getCustomerName(), request.getCustomerPhone());
        return toResponse(jc);
    }

    /** Shared by the endpoint and the Receipt flow (a receipt that links a customerless job card). */
    @Transactional
    public void attachCustomerInternal(Long orgId, JobCard jc, Long customerId, String customerName, String customerPhone) {
        AppUser me = userRepo.findByIdAndOrganization_Id(branchScope.currentUserId(), orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));
        String ref = JobCardResponse.reference(
            branchRepo.findByIdAndOrgId(jc.getBranchId(), orgId).map(Branch::getCode).orElse("?"), jc.getId());
        if (jc.getCustomerId() != null) {
            throw DamsException.conflict("Job card " + ref + " already has a customer - it cannot be changed here");
        }
        if (ActingDetails.effectiveRole(me) != Role.CASHIER
            || !jc.getBranchId().equals(ActingDetails.effectiveHomeBranch(me))) {
            throw DamsException.forbidden("Only a cashier of " + ref + "'s branch can attach its customer");
        }
        if (customerId == null && (customerName == null || customerName.isBlank())) {
            throw DamsException.badRequest("Provide customerId or customerName");
        }

        PartyResolver.Party party = partyResolver.resolve(orgId, customerId, customerName, customerPhone,
            null, null, jc.getBranchId());
        Customer customer = party.customer();
        jc.setCustomerId(customer.getId());
        if (jc.getVehicleNoText() != null) {
            Vehicle v = partyResolver.vehicleForCustomer(orgId, customer, jc.getVehicleNoText());
            jc.setVehicleId(v.getId());
            jc.setVehicleNoText(null);
        }
        jobCardRepo.save(jc);
        auditService.recordUserEvent(ENTITY, jc.getId(), jc.getBranchId(), EventType.JOB_CARD_CUSTOMER_ATTACHED,
            me.getId(), orderedDetail("customerId", customer.getId(), "vehicleId", jc.getVehicleId()));
        log.info("JobCard customer attached: orgId={} jobCardId={} customerId={}", orgId, jc.getId(), customer.getId());
    }

    private static final Pattern REFERENCE = Pattern.compile("^[A-Za-z0-9]+-JC-(\\d+)$");
    private static final int SEARCH_LIMIT = 20;

    /**
     * Branch-scoped job-card search for the pickers. Matches the customer's name/phone, the
     * vehicle number (linked or typed-only), DBM id, invoice no, the internal id and the
     * {@code {branchCode}-JC-{id}} reference. An empty {@code q} lists the newest job cards.
     */
    @Transactional(readOnly = true)
    public List<JobCardSearchHit> search(String q, Long customerId, Long vehicleId) {
        Long orgId = TenantContext.requireOrgId();
        Optional<Set<Long>> allowed = branchScope.allowedBranchIds();
        if (allowed.isPresent() && allowed.get().isEmpty()) {
            return List.of();
        }
        String text = q == null ? "" : q.trim();
        Long idQ = null;
        Matcher m = REFERENCE.matcher(text);
        if (m.matches()) {
            idQ = Long.valueOf(m.group(1));
        } else if (text.matches("\\d{1,18}")) {
            idQ = Long.valueOf(text);
        }
        String vehicleFrag = Vehicle.normalise(text);
        // A "#" never occurs in a normalised vehicle number, so this pattern matches nothing when q has no letters/digits.
        String vLike = (vehicleFrag == null || vehicleFrag.isBlank()) ? "#" : "%" + vehicleFrag + "%";
        List<JobCard> hits = jobCardRepo.searchForPicker(orgId, allowed.isEmpty(),
            allowed.orElse(Set.of(-1L)), customerId, vehicleId, text.isEmpty(),
            "%" + text.toLowerCase() + "%", vLike, idQ, Limit.of(SEARCH_LIMIT));

        Map<Long, Customer> customers = customerRepo.findByOrgIdAndIdInOrderByNameAsc(orgId,
            hits.stream().map(JobCard::getCustomerId).filter(Objects::nonNull).distinct().toList())
            .stream().collect(Collectors.toMap(Customer::getId, c -> c));
        Map<Long, Vehicle> vehicles = vehicleRepo.findByOrgIdAndIdIn(orgId,
            hits.stream().map(JobCard::getVehicleId).filter(Objects::nonNull).distinct().toList())
            .stream().collect(Collectors.toMap(Vehicle::getId, v -> v));
        Map<Long, Branch> branches = new HashMap<>();
        for (Branch b : branchRepo.findByOrgIdOrderByCodeAsc(orgId)) {
            branches.put(b.getId(), b);
        }
        return hits.stream().map(j -> {
            Customer c = j.getCustomerId() == null ? null : customers.get(j.getCustomerId());
            Vehicle v = j.getVehicleId() == null ? null : vehicles.get(j.getVehicleId());
            Branch b = branches.get(j.getBranchId());
            String code = b != null ? b.getCode() : "?";
            return new JobCardSearchHit(j.getId(), JobCardResponse.reference(code, j.getId()), j.getBranchId(), code,
                j.getCustomerId(), c != null ? c.getName() : null,
                j.getVehicleId(), v != null ? v.getVehicleNo() : j.getVehicleNoText(),
                j.getDbmId(), j.getInvoiceNo(), j.getCategoryId(), j.getCreatedAt());
        }).toList();
    }

    private static Map<String, Object> orderedDetail(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
