package com.dams.jobcard.service;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.customer.entity.Customer;
import com.dams.customer.repository.CustomerRepository;
import com.dams.jobcard.dto.JobCardCreateRequest;
import com.dams.jobcard.dto.JobCardPatchRequest;
import com.dams.jobcard.dto.JobCardResponse;
import com.dams.jobcard.entity.ClaimClose;
import com.dams.jobcard.entity.JobCard;
import com.dams.jobcard.repository.ClaimCloseRepository;
import com.dams.jobcard.repository.JobCardRepository;
import com.dams.receive.service.ReceivePaymentGuard;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.user.entity.AppUser;
import com.dams.user.entity.Role;
import com.dams.user.repository.AppUserRepository;
import com.dams.vehicle.entity.Vehicle;
import com.dams.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Job-card (case) lifecycle for Stage 3: create (with inline customer/vehicle), read with
 * derived fields, and PATCH.
 *
 * PATCH rules (plan.md): invoice_no / invoice_amount / dbm_id are editable at any time;
 * category_id / business_status_id are editable only while the job card has no ClaimClose
 * row — that table arrives in Stage 8, so {@link #hasClaimClose} is a stub returning false
 * for now, and the 409 path it guards is unreachable this stage but already wired. A
 * category change writes a CATEGORY_CHANGED audit event with the before/after ids.
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
    private final AppUserRepository userRepo;
    private final BranchScope branchScope;
    private final AuditService auditService;
    private final PendingAmountCalculator pendingAmountCalculator;
    private final ClaimCloseRepository claimCloseRepo;
    private final ReceivePaymentGuard paymentGuard;

    public JobCardService(JobCardRepository jobCardRepo,
                          CustomerRepository customerRepo,
                          VehicleRepository vehicleRepo,
                          BranchRepository branchRepo,
                          ReceiveCategoryRepository categoryRepo,
                          ReceiveBusinessStatusRepository statusRepo,
                          AppUserRepository userRepo,
                          BranchScope branchScope,
                          AuditService auditService,
                          PendingAmountCalculator pendingAmountCalculator,
                          ClaimCloseRepository claimCloseRepo,
                          ReceivePaymentGuard paymentGuard) {
        this.jobCardRepo = jobCardRepo;
        this.customerRepo = customerRepo;
        this.vehicleRepo = vehicleRepo;
        this.branchRepo = branchRepo;
        this.categoryRepo = categoryRepo;
        this.statusRepo = statusRepo;
        this.userRepo = userRepo;
        this.branchScope = branchScope;
        this.auditService = auditService;
        this.pendingAmountCalculator = pendingAmountCalculator;
        this.claimCloseRepo = claimCloseRepo;
        this.paymentGuard = paymentGuard;
    }

    @Transactional(readOnly = true)
    public JobCardResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional
    public JobCardResponse create(JobCardCreateRequest request) {
        Long orgId = TenantContext.requireOrgId();

        Customer customer = resolveCustomer(orgId, request);
        Vehicle vehicle = resolveVehicle(orgId, customer, request);
        Long branchId = resolvePostingBranch(orgId, request.getBranchId());

        ReceiveCategory category = requireActiveCategory(orgId, request.getCategoryId());
        ReceiveBusinessStatus status = requireActiveStatus(orgId, request.getBusinessStatusId());

        JobCard jc = new JobCard();
        jc.setOrgId(orgId);
        jc.setBranchId(branchId);
        jc.setCustomerId(customer.getId());
        jc.setVehicleId(vehicle != null ? vehicle.getId() : null);
        jc.setDbmId(blankToNull(request.getDbmId()));
        jc.setInvoiceNo(blankToNull(request.getInvoiceNo()));
        jc.setInvoiceAmount(request.getInvoiceAmount());
        boolean b2b = Boolean.TRUE.equals(request.getB2b());
        jc.setB2b(b2b);
        jc.setGstNo(blankToNull(request.getGstNo()));
        requireGstWhenB2b(b2b, jc.getGstNo());
        jc.setCategoryId(category.getId());
        jc.setBusinessStatusId(status.getId());
        jc = jobCardRepo.save(jc);

        auditService.recordUserEvent(ENTITY, jc.getId(), EventType.CREATED, branchScope.currentUserId(),
            Map.of("customerId", customer.getId(), "branchId", branchId, "categoryId", category.getId()));

        log.info("JobCard created: orgId={} jobCardId={} branchId={} customerId={}",
            orgId, jc.getId(), branchId, customer.getId());
        return toResponse(jc);
    }

    @Transactional
    public JobCardResponse patch(Long id, JobCardPatchRequest request) {
        Long orgId = TenantContext.requireOrgId();
        JobCard jc = load(id);

        // Free-to-edit references
        if (request.getInvoiceNo() != null) {
            jc.setInvoiceNo(blankToNull(request.getInvoiceNo()));
        }
        if (request.getInvoiceAmount() != null) {
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
        // Validate against the effective (post-patch) values.
        requireGstWhenB2b(jc.isB2b(), jc.getGstNo());

        boolean wantsCategoryChange = request.getCategoryId() != null
            && !request.getCategoryId().equals(jc.getCategoryId());
        boolean wantsStatusChange = request.getBusinessStatusId() != null
            && !request.getBusinessStatusId().equals(jc.getBusinessStatusId());

        if ((wantsCategoryChange || wantsStatusChange) && hasClaimClose(jc.getId())) {
            throw DamsException.conflict(
                "This job card's claim is closed — category and business status can no longer be changed");
        }

        if (wantsCategoryChange) {
            Long before = jc.getCategoryId();
            ReceiveCategory next = requireActiveCategory(orgId, request.getCategoryId());
            jc.setCategoryId(next.getId());
            auditService.recordUserEvent(ENTITY, jc.getId(), EventType.CATEGORY_CHANGED,
                branchScope.currentUserId(),
                orderedDetail("before", before, "after", next.getId()));
        }
        if (wantsStatusChange) {
            ReceiveBusinessStatus next = requireActiveStatus(orgId, request.getBusinessStatusId());
            jc.setBusinessStatusId(next.getId());
        }

        jc = jobCardRepo.save(jc);
        log.info("JobCard patched: orgId={} jobCardId={} categoryChanged={} statusChanged={}",
            orgId, jc.getId(), wantsCategoryChange, wantsStatusChange);
        return toResponse(jc);
    }

    // --- resolution helpers ---

    private Customer resolveCustomer(Long orgId, JobCardCreateRequest request) {
        if (request.getCustomerId() != null) {
            return customerRepo.findByIdAndOrgId(request.getCustomerId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Customer", request.getCustomerId()));
        }
        if (request.getCustomerName() == null || request.getCustomerName().isBlank()) {
            throw DamsException.badRequest("Provide customerId or customerName for the job card");
        }
        Customer c = new Customer();
        c.setOrgId(orgId);
        c.setName(request.getCustomerName().trim());
        c.setPhone(blankToNull(request.getCustomerPhone()));
        c = customerRepo.save(c);
        log.info("Customer created inline for job card: orgId={} customerId={}", orgId, c.getId());
        return c;
    }

    private Vehicle resolveVehicle(Long orgId, Customer customer, JobCardCreateRequest request) {
        if (request.getVehicleId() != null) {
            Vehicle v = vehicleRepo.findByIdAndOrgId(request.getVehicleId(), orgId)
                .orElseThrow(() -> DamsException.notFound("Vehicle", request.getVehicleId()));
            return v;
        }
        String normalised = Vehicle.normalise(request.getVehicleNo());
        if (normalised == null || normalised.isBlank()) {
            return null; // counter sale — no vehicle
        }
        return vehicleRepo.findByOrgIdAndVehicleNo(orgId, normalised).orElseGet(() -> {
            Vehicle v = new Vehicle();
            v.setOrgId(orgId);
            v.setCustomerId(customer.getId());
            v.setVehicleNo(normalised);
            Vehicle saved = vehicleRepo.save(v);
            log.info("Vehicle created inline for job card: orgId={} vehicleId={} no={}", orgId, saved.getId(), normalised);
            return saved;
        });
    }

    /**
     * CASHIER: always their home branch (the request's branchId is ignored).
     * Everyone else: the requested branch, which must exist and be within their branch scope.
     */
    private Long resolvePostingBranch(Long orgId, Long requestedBranchId) {
        AppUser me = userRepo.findByIdAndOrganization_Id(branchScope.currentUserId(), orgId)
            .orElseThrow(() -> DamsException.forbidden("The signed-in user is not part of this organization"));

        if (me.getRole() == Role.CASHIER) {
            if (me.getHomeBranchId() == null) {
                throw DamsException.badRequest("Your account has no home branch — ask an Owner to set one");
            }
            return me.getHomeBranchId();
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

    private JobCardResponse toResponse(JobCard jc) {
        Long orgId = jc.getOrgId();
        Branch branch = branchRepo.findByIdAndOrgId(jc.getBranchId(), orgId).orElse(null);
        Customer customer = customerRepo.findByIdAndOrgId(jc.getCustomerId(), orgId).orElse(null);
        Vehicle vehicle = jc.getVehicleId() == null ? null
            : vehicleRepo.findByIdAndOrgId(jc.getVehicleId(), orgId).orElse(null);
        ReceiveCategory category = categoryRepo.findByIdAndOrgId(jc.getCategoryId(), orgId).orElse(null);
        ReceiveBusinessStatus status = statusRepo.findByIdAndOrgId(jc.getBusinessStatusId(), orgId).orElse(null);

        String branchCode = branch != null ? branch.getCode() : "?";

        ClaimClose claimClose = claimCloseRepo.findByOrgIdAndJobCardId(orgId, jc.getId()).orElse(null);
        BigDecimal pending = pendingAmountCalculator.forJobCard(jc);
        boolean claimClosed = claimClose != null;

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
            vehicle != null ? vehicle.getVehicleNo() : null,
            jc.getDbmId(),
            jc.getInvoiceNo(),
            jc.getInvoiceAmount(),
            jc.isB2b(),
            jc.getGstNo(),
            jc.getCategoryId(),
            category != null ? category.getName() : null,
            category != null && category.isClaim(),
            jc.getBusinessStatusId(),
            status != null ? status.getName() : null,
            pending,
            claimClosed,
            claimClose != null ? claimClose.getFinalAmount() : null,
            paymentGuard.canRecordPayment(orgId, jc, pending, claimClosed),
            jc.getCreatedAt());
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
