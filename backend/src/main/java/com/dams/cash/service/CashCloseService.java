package com.dams.cash.service;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.branch.entity.Branch;
import com.dams.branch.repository.BranchRepository;
import com.dams.cash.dto.CashDayCloseResponse;
import com.dams.cash.dto.CashDocumentResponse;
import com.dams.cash.dto.CashDrawerResponse;
import com.dams.cash.dto.CashOpeningRequest;
import com.dams.cash.dto.CloseDayRequest;
import com.dams.cash.entity.BranchCashOpening;
import com.dams.cash.entity.CashDayClose;
import com.dams.cash.entity.CashDocument;
import com.dams.cash.repository.BranchCashOpeningRepository;
import com.dams.cash.repository.CashDayCloseRepository;
import com.dams.cash.repository.CashDocumentRepository;
import com.dams.common.exception.DamsException;
import com.dams.common.time.OrgTime;
import com.dams.config.TenantContext;
import com.dams.user.entity.AppUser;
import com.dams.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Cash-page day operations: the Accountant's one-time branch opening, the cashier's
 * end-of-day close (counted amount → variance → date lock), and the assembled live drawer
 * view. The drawer math itself lives in {@link DrawerService}.
 */
@Service
public class CashCloseService {

    private static final Logger log = LoggerFactory.getLogger(CashCloseService.class);

    private final CashDayCloseRepository cashDayCloseRepo;
    private final BranchCashOpeningRepository branchCashOpeningRepo;
    private final CashDocumentRepository cashDocumentRepo;
    private final DrawerService drawerService;
    private final CashDocumentService cashDocumentService;
    private final BranchRepository branchRepo;
    private final AppUserRepository userRepo;
    private final CashPostingGuard guard;
    private final AuditService auditService;

    public CashCloseService(CashDayCloseRepository cashDayCloseRepo,
                            BranchCashOpeningRepository branchCashOpeningRepo,
                            CashDocumentRepository cashDocumentRepo,
                            DrawerService drawerService,
                            CashDocumentService cashDocumentService,
                            BranchRepository branchRepo,
                            AppUserRepository userRepo,
                            CashPostingGuard guard,
                            AuditService auditService) {
        this.cashDayCloseRepo = cashDayCloseRepo;
        this.branchCashOpeningRepo = branchCashOpeningRepo;
        this.cashDocumentRepo = cashDocumentRepo;
        this.drawerService = drawerService;
        this.cashDocumentService = cashDocumentService;
        this.branchRepo = branchRepo;
        this.userRepo = userRepo;
        this.guard = guard;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ drawer

    @Transactional(readOnly = true)
    public CashDrawerResponse drawer(Long requestedBranchId, LocalDate date) {
        Long orgId = TenantContext.requireOrgId();
        Long branchId = guard.resolveViewBranch(orgId, requestedBranchId);
        Branch branch = branchRepo.findByIdAndOrgId(branchId, orgId).orElse(null);

        DrawerService.DrawerPosition p = drawerService.position(orgId, branchId, date);
        CashDayClose close = cashDayCloseRepo.findByOrgIdAndBranchIdAndCloseDate(orgId, branchId, date).orElse(null);
        List<CashDocumentResponse> movements = cashDocumentRepo
            .findByOrgIdAndBranchIdAndTransactionDateOrderByIdAsc(orgId, branchId, date)
            .stream().map(cashDocumentService::assemble).toList();

        return new CashDrawerResponse(
            branchId,
            branch != null ? branch.getCode() : null,
            branch != null ? branch.getName() : null,
            date,
            p.opening(),
            p.openingSet(),
            p.cashReceipts(),
            p.cashIn(),
            p.cashExpenses(),
            p.cashOut(),
            p.computedPosition(),
            close != null,
            close != null ? toCloseResponse(orgId, close) : null,
            movements);
    }

    // ------------------------------------------------------------------ opening

    @Transactional
    public CashDrawerResponse setOpening(CashOpeningRequest request) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = guard.requireAccountantForBranch(orgId, request.getBranchId());

        if (branchCashOpeningRepo.existsByOrgIdAndBranchId(orgId, request.getBranchId())) {
            throw DamsException.conflict("Branch "
                + branchRepo.findByIdAndOrgId(request.getBranchId(), orgId).map(Branch::getCode).orElse("?")
                + " already has an opening cash balance — every day after that is the previous day's counted close");
        }
        BranchCashOpening opening = new BranchCashOpening();
        opening.setOrgId(orgId);
        opening.setBranchId(request.getBranchId());
        opening.setOpeningDate(request.getOpeningDate());
        opening.setAmount(request.getAmount());
        opening.setSetBy(me.getId());
        opening = branchCashOpeningRepo.save(opening);

        auditService.recordUserEvent("BranchCashOpening", opening.getId(), EventType.CREATED, me.getId(),
            orderedDetail("branchId", request.getBranchId(), "amount", request.getAmount()));
        log.info("Branch cash opening set: orgId={} branchId={} amount={} by userId={}",
            orgId, request.getBranchId(), request.getAmount(), me.getId());

        return drawer(request.getBranchId(), request.getOpeningDate());
    }

    // ------------------------------------------------------------------ close

    @Transactional
    public CashDayCloseResponse closeDay(CloseDayRequest request) {
        Long orgId = TenantContext.requireOrgId();
        AppUser me = guard.requireCashier(orgId);
        Long branchId = me.getHomeBranchId();
        LocalDate closeDate = request.getCloseDate();

        if (closeDate.isAfter(OrgTime.today())) {
            throw DamsException.badRequest("Cannot close a future date (" + closeDate + ")");
        }
        cashDayCloseRepo.findFirstByOrgIdAndBranchIdOrderByCloseDateDesc(orgId, branchId).ifPresent(latest -> {
            if (!closeDate.isAfter(latest.getCloseDate())) {
                throw DamsException.conflict("Cash for this branch is already closed through "
                    + latest.getCloseDate() + " — " + closeDate + " cannot be closed again");
            }
        });

        DrawerService.DrawerPosition p = drawerService.position(orgId, branchId, closeDate);
        BigDecimal computed = p.computedPosition();
        BigDecimal counted = request.getCountedAmount();
        BigDecimal variance = counted.subtract(computed);

        if (variance.signum() != 0 && blank(request.getVarianceRemark())) {
            throw DamsException.badRequest("The counted amount (" + counted + ") differs from the computed"
                + " position (" + computed + ") by " + variance + " — a variance remark is required");
        }

        CashDayClose close = new CashDayClose();
        close.setOrgId(orgId);
        close.setBranchId(branchId);
        close.setCloseDate(closeDate);
        close.setOpeningAmount(p.opening());
        close.setComputedClosing(computed);
        close.setCountedAmount(counted);
        close.setVariance(variance);
        close.setVarianceRemark(variance.signum() != 0 ? request.getVarianceRemark().trim() : null);
        close.setClosedBy(me.getId());
        close = cashDayCloseRepo.save(close);

        auditService.recordUserEvent("CashDayClose", close.getId(), EventType.CLOSED, me.getId(),
            orderedDetail("closeDate", closeDate.toString(), "variance", variance));
        log.info("Cash day closed: orgId={} branchId={} date={} computed={} counted={} variance={}",
            orgId, branchId, closeDate, computed, counted, variance);
        return toCloseResponse(orgId, close);
    }

    @Transactional(readOnly = true)
    public CashDayCloseResponse getClose(Long requestedBranchId, LocalDate date) {
        Long orgId = TenantContext.requireOrgId();
        Long branchId = guard.resolveViewBranch(orgId, requestedBranchId);
        CashDayClose close = cashDayCloseRepo.findByOrgIdAndBranchIdAndCloseDate(orgId, branchId, date)
            .orElseThrow(() -> DamsException.notFound("Cash day close", "date", date));
        return toCloseResponse(orgId, close);
    }

    // ------------------------------------------------------------------ helpers

    private CashDayCloseResponse toCloseResponse(Long orgId, CashDayClose c) {
        String branchCode = branchRepo.findByIdAndOrgId(c.getBranchId(), orgId).map(Branch::getCode).orElse(null);
        String closedByName = userRepo.findById(c.getClosedBy()).map(AppUser::getName).orElse(null);
        return new CashDayCloseResponse(
            c.getId(), c.getBranchId(), branchCode, c.getCloseDate(),
            c.getOpeningAmount(), c.getComputedClosing(), c.getCountedAmount(),
            c.getVariance(), c.getVarianceRemark(), c.getClosedBy(), closedByName, c.getClosedAt());
    }

    private static Map<String, Object> orderedDetail(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
