package com.dams.staff.service;

import com.dams.audit.entity.EventType;
import com.dams.audit.service.AuditService;
import com.dams.common.exception.DamsException;
import com.dams.common.security.BranchScope;
import com.dams.config.TenantContext;
import com.dams.staff.dto.CreateAdvanceEntryRequest;
import com.dams.staff.dto.CreateStaffRequest;
import com.dams.staff.dto.StaffAdvanceEntryResponse;
import com.dams.staff.dto.StaffMemberResponse;
import com.dams.staff.entity.StaffAdvanceEntry;
import com.dams.staff.entity.StaffMember;
import com.dams.staff.repository.StaffAdvanceEntryRepository;
import com.dams.staff.repository.StaffMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Staff advance ledger (FEAT-44). Advances go out, recoveries come back in,
 * outstanding is derived per staff. Staff-wide (not branch-scoped): advances
 * are an org matter between employer and employee. Writers are the
 * transacting roles; the auditor reads.
 */
@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    private final StaffMemberRepository staffRepo;
    private final StaffAdvanceEntryRepository entryRepo;
    private final BranchScope branchScope;
    private final AuditService auditService;

    public StaffService(StaffMemberRepository staffRepo,
                        StaffAdvanceEntryRepository entryRepo,
                        BranchScope branchScope,
                        AuditService auditService) {
        this.staffRepo = staffRepo;
        this.entryRepo = entryRepo;
        this.branchScope = branchScope;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StaffMemberResponse> members() {
        Long orgId = TenantContext.requireOrgId();
        List<StaffMemberResponse> out = new ArrayList<>();
        for (StaffMember s : staffRepo.findByOrgIdOrderByNameAsc(orgId)) {
            out.add(new StaffMemberResponse(s.getId(), s.getName(), s.getPhone(), s.isActive(),
                entryRepo.outstandingFor(orgId, s.getId())));
        }
        return out;
    }

    @Transactional
    public StaffMemberResponse addMember(CreateStaffRequest request) {
        Long orgId = TenantContext.requireOrgId();
        String name = request.getName().trim();
        var existing = staffRepo.findByOrgIdAndNameIgnoreCase(orgId, name);
        if (existing.isPresent()) {
            StaffMember s = existing.get();
            // Re-adding a deactivated name reactivates instead of duplicating.
            s.setActive(true);
            if (request.getPhone() != null && !request.getPhone().isBlank()) {
                s.setPhone(request.getPhone().trim());
            }
            s = staffRepo.save(s);
            return toResponse(orgId, s);
        }
        StaffMember s = new StaffMember();
        s.setOrgId(orgId);
        s.setName(name);
        s.setPhone(request.getPhone() == null || request.getPhone().isBlank() ? null : request.getPhone().trim());
        s = staffRepo.save(s);
        auditService.recordUserEvent("StaffMember", s.getId(), EventType.CREATED,
            branchScope.currentUserId(), Map.of("name", name));
        log.info("Staff member added: orgId={} staffId={} by={}", orgId, s.getId(), branchScope.currentUserId());
        return toResponse(orgId, s);
    }

    @Transactional
    public StaffMemberResponse deactivate(Long staffId) {
        Long orgId = TenantContext.requireOrgId();
        StaffMember s = load(staffId, orgId);
        s.setActive(false);
        s = staffRepo.save(s);
        log.info("Staff member deactivated: orgId={} staffId={} by={}", orgId, staffId, branchScope.currentUserId());
        return toResponse(orgId, s);
    }

    @Transactional(readOnly = true)
    public List<StaffAdvanceEntryResponse> entries(Long staffId) {
        Long orgId = TenantContext.requireOrgId();
        load(staffId, orgId);
        return entryRepo.findByOrgIdAndStaffIdOrderByTxnDateDescIdDesc(orgId, staffId).stream()
            .map(StaffAdvanceEntryResponse::of).toList();
    }

    @Transactional
    public StaffAdvanceEntryResponse recordEntry(Long staffId, CreateAdvanceEntryRequest request) {
        Long orgId = TenantContext.requireOrgId();
        StaffMember s = load(staffId, orgId);
        if (!s.isActive()) {
            throw DamsException.conflict("Staff member '" + s.getName() + "' is deactivated");
        }
        String kind = request.getKind() == null ? "" : request.getKind().trim().toUpperCase();
        if (!StaffAdvanceEntry.ADVANCE.equals(kind) && !StaffAdvanceEntry.RECOVERY.equals(kind)) {
            throw DamsException.badRequest("kind must be ADVANCE or RECOVERY");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw DamsException.badRequest("amount must be greater than zero");
        }
        if (StaffAdvanceEntry.RECOVERY.equals(kind)) {
            BigDecimal outstanding = entryRepo.outstandingFor(orgId, staffId);
            if (request.getAmount().compareTo(outstanding) > 0) {
                throw DamsException.badRequest("Recovery of " + request.getAmount()
                    + " exceeds the outstanding " + outstanding + " for " + s.getName());
            }
        }
        StaffAdvanceEntry e = new StaffAdvanceEntry();
        e.setOrgId(orgId);
        e.setStaffId(staffId);
        e.setKind(kind);
        e.setAmount(request.getAmount());
        e.setTxnDate(request.getTxnDate());
        e.setNote(request.getNote() == null ? null : request.getNote().trim());
        e.setCreatedBy(branchScope.currentUserId());
        e = entryRepo.save(e);
        auditService.recordUserEvent("StaffAdvanceEntry", e.getId(), EventType.CREATED,
            branchScope.currentUserId(),
            Map.of("staffId", staffId, "kind", kind, "amount", request.getAmount()));
        log.info("Staff advance entry: orgId={} staffId={} kind={} amount={} by={}",
            orgId, staffId, kind, request.getAmount(), branchScope.currentUserId());
        return StaffAdvanceEntryResponse.of(e);
    }

    private StaffMember load(Long staffId, Long orgId) {
        return staffRepo.findByIdAndOrgId(staffId, orgId)
            .orElseThrow(() -> DamsException.notFound("Staff member", staffId));
    }

    private StaffMemberResponse toResponse(Long orgId, StaffMember s) {
        return new StaffMemberResponse(s.getId(), s.getName(), s.getPhone(), s.isActive(),
            entryRepo.outstandingFor(orgId, s.getId()));
    }
}
