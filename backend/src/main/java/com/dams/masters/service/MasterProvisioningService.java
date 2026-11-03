package com.dams.masters.service;

import com.dams.masters.entity.Bank;
import com.dams.masters.entity.ClaimType;
import com.dams.masters.entity.ExpenseBusinessStatus;
import com.dams.masters.entity.ExpenseCategory;
import com.dams.masters.entity.ExpenseMode;
import com.dams.masters.entity.ExpenseSubCategory;
import com.dams.masters.entity.ReceiveBusinessStatus;
import com.dams.masters.entity.ReceiveBusinessStatusRole;
import com.dams.masters.entity.ReceiveCategory;
import com.dams.masters.entity.SettlementMode;
import com.dams.masters.repository.BankRepository;
import com.dams.masters.repository.ClaimTypeRepository;
import com.dams.masters.repository.ExpenseBusinessStatusRepository;
import com.dams.masters.repository.ExpenseCategoryRepository;
import com.dams.masters.repository.ExpenseModeRepository;
import com.dams.masters.repository.ExpenseSubCategoryRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRepository;
import com.dams.masters.repository.ReceiveBusinessStatusRoleRepository;
import com.dams.masters.repository.ReceiveCategoryRepository;
import com.dams.masters.repository.SettlementModeRepository;
import com.dams.organization.entity.Organization;
import com.dams.organization.repository.OrganizationRepository;
import com.dams.user.entity.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Seeds a brand-new organization with the standard master catalogue.
 *
 * rev 88 — the standard is the template organization (the demo dealership, {@code master_template}):
 * its ACTIVE list items are copied with their flags, limits, sort order and the roles allowed to pick
 * each receipt status, and its two Owner switches are copied onto the new organization. Retired receipt
 * statuses, UPI IDs and vendors are not copied. Only when there is no template (a fresh database) does
 * the built-in catalogue below apply — the same set the demo dealership first shipped with (Flyway V5).
 *
 * Without this, a freshly-onboarded Owner signs in to empty dropdowns everywhere and can't
 * record a single receipt until every mode, category and status has been typed by hand.
 * Runs inside {@code AdminOrgService.createOrganization}'s transaction.
 */
@Service
public class MasterProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(MasterProvisioningService.class);

    private final ReceiveCategoryRepository receiveCategoryRepo;
    private final ReceiveBusinessStatusRepository receiveBusinessStatusRepo;
    private final ReceiveBusinessStatusRoleRepository receiveBusinessStatusRoleRepo;
    private final SettlementModeRepository settlementModeRepo;
    private final ExpenseCategoryRepository expenseCategoryRepo;
    private final ExpenseSubCategoryRepository expenseSubCategoryRepo;
    private final ExpenseModeRepository expenseModeRepo;
    private final ExpenseBusinessStatusRepository expenseBusinessStatusRepo;
    private final BankRepository bankRepo;
    private final ClaimTypeRepository claimTypeRepo;
    private final OrganizationRepository organizationRepo;

    public MasterProvisioningService(ReceiveCategoryRepository receiveCategoryRepo,
                                     ReceiveBusinessStatusRepository receiveBusinessStatusRepo,
                                     ReceiveBusinessStatusRoleRepository receiveBusinessStatusRoleRepo,
                                     SettlementModeRepository settlementModeRepo,
                                     ExpenseCategoryRepository expenseCategoryRepo,
                                     ExpenseSubCategoryRepository expenseSubCategoryRepo,
                                     ExpenseModeRepository expenseModeRepo,
                                     ExpenseBusinessStatusRepository expenseBusinessStatusRepo,
                                     BankRepository bankRepo,
                                     ClaimTypeRepository claimTypeRepo,
                                     OrganizationRepository organizationRepo) {
        this.receiveCategoryRepo = receiveCategoryRepo;
        this.receiveBusinessStatusRepo = receiveBusinessStatusRepo;
        this.receiveBusinessStatusRoleRepo = receiveBusinessStatusRoleRepo;
        this.settlementModeRepo = settlementModeRepo;
        this.expenseCategoryRepo = expenseCategoryRepo;
        this.expenseSubCategoryRepo = expenseSubCategoryRepo;
        this.expenseModeRepo = expenseModeRepo;
        this.expenseBusinessStatusRepo = expenseBusinessStatusRepo;
        this.bankRepo = bankRepo;
        this.claimTypeRepo = claimTypeRepo;
        this.organizationRepo = organizationRepo;
    }

    public void provisionDefaults(Long orgId) {
        Optional<Organization> template = organizationRepo.findFirstByMasterTemplateTrue()
            .filter(t -> !t.getId().equals(orgId));
        if (template.isPresent()) {
            copyFromTemplate(template.get(), orgId);
        } else {
            provisionBuiltInDefaults(orgId);
        }
    }

    /** rev 88 — the template organization's active list items and Owner switches, onto {@code orgId}. */
    private void copyFromTemplate(Organization template, Long orgId) {
        Long from = template.getId();

        receiveCategoryRepo.saveAll(activeOf(receiveCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(from)).stream()
            .map(t -> {
                ReceiveCategory c = named(new ReceiveCategory(), orgId, t.getName(), t.getSortOrder());
                c.setClaim(t.isClaim());
                return c;
            }).toList());

        claimTypeRepo.saveAll(activeOf(claimTypeRepo.findByOrgIdOrderBySortOrderAscIdAsc(from)).stream()
            .map(t -> named(new ClaimType(), orgId, t.getName(), t.getSortOrder())).toList());

        // Retired statuses exist only so the template's old job cards still read — not carried over.
        Map<Long, List<ReceiveBusinessStatusRole>> rolesByStatus = new HashMap<>();
        for (ReceiveBusinessStatusRole g : receiveBusinessStatusRoleRepo.findByOrgId(from)) {
            rolesByStatus.computeIfAbsent(g.getStatusId(), k -> new java.util.ArrayList<>()).add(g);
        }
        for (ReceiveBusinessStatus t : activeOf(receiveBusinessStatusRepo.findByOrgIdOrderBySortOrderAscIdAsc(from))) {
            if (t.isDeprecated()) {
                continue;
            }
            ReceiveBusinessStatus status =
                receiveBusinessStatusRepo.save(named(new ReceiveBusinessStatus(), orgId, t.getName(), t.getSortOrder()));
            for (ReceiveBusinessStatusRole g : rolesByStatus.getOrDefault(t.getId(), List.of())) {
                ReceiveBusinessStatusRole grant = new ReceiveBusinessStatusRole();
                grant.setOrgId(orgId);
                grant.setStatusId(status.getId());
                grant.setRole(g.getRole());
                receiveBusinessStatusRoleRepo.save(grant);
            }
        }

        settlementModeRepo.saveAll(activeOf(settlementModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(from)).stream()
            .map(t -> settlementMode(orgId, t.getName(), t.isRequiresBank(), t.isRequiresRef(), t.isCash(), t.getSortOrder()))
            .toList());

        Map<Long, Long> categoryId = new HashMap<>();   // template category id -> new category id
        for (ExpenseCategory t : activeOf(expenseCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(from))) {
            categoryId.put(t.getId(),
                expenseCategoryRepo.save(named(new ExpenseCategory(), orgId, t.getName(), t.getSortOrder())).getId());
        }
        expenseSubCategoryRepo.saveAll(activeOf(expenseSubCategoryRepo.findByOrgIdOrderBySortOrderAscIdAsc(from)).stream()
            .filter(t -> categoryId.containsKey(t.getExpenseCategoryId()))
            .map(t -> {
                ExpenseSubCategory m = named(new ExpenseSubCategory(), orgId, t.getName(), t.getSortOrder());
                m.setExpenseCategoryId(categoryId.get(t.getExpenseCategoryId()));
                m.setLimitAmount(t.getLimitAmount());
                return m;
            }).toList());

        expenseModeRepo.saveAll(activeOf(expenseModeRepo.findByOrgIdOrderBySortOrderAscIdAsc(from)).stream()
            .map(t -> expenseMode(orgId, t.getName(), t.isRequiresBank(), t.isRequiresRef(), t.isCash(), t.getSortOrder()))
            .toList());

        expenseBusinessStatusRepo.saveAll(activeOf(expenseBusinessStatusRepo.findByOrgIdOrderBySortOrderAscIdAsc(from)).stream()
            .map(t -> {
                ExpenseBusinessStatus m = expenseBusinessStatus(orgId, t.getName(), t.isTriggersClaim(), t.getSortOrder());
                m.setRequiresFmApproval(t.isRequiresFmApproval());
                return m;
            }).toList());

        bankRepo.saveAll(activeOf(bankRepo.findByOrgIdOrderBySortOrderAscIdAsc(from)).stream()
            .map(t -> named(new Bank(), orgId, t.getName(), t.getSortOrder())).toList());

        // The Owner's Settings switches.
        organizationRepo.findById(orgId).ifPresent(org -> {
            org.setAccountantDirectApproveCash(template.isAccountantDirectApproveCash());
            org.setMultiBranchCashierAccess(template.isMultiBranchCashierAccess());
            organizationRepo.save(org);
        });

        log.info("Provisioned masters for orgId={} from template orgId={}", orgId, from);
    }

    private static <T extends com.dams.common.entity.OrgMaster> List<T> activeOf(List<T> rows) {
        return rows.stream().filter(com.dams.common.entity.OrgMaster::isActive).toList();
    }

    /** The built-in catalogue — used only when no template organization exists (a fresh database). */
    private void provisionBuiltInDefaults(Long orgId) {
        receiveCategoryRepo.saveAll(List.of(
            receiveCategory(orgId, "Workshop", 1),
            receiveCategory(orgId, "Breakdown", 2),
            receiveCategory(orgId, "Advance", 3),
            receiveCategory(orgId, "Spare / Counter", 4),
            receiveCategory(orgId, "AdBlue Bucket", 5),
            receiveCategory(orgId, "AdBlue Barrel", 6),
            receiveCategory(orgId, "B2B Credit", 7),
            receiveCategory(orgId, "Scrap / Used Lubes / Other", 8)));

        claimTypeRepo.saveAll(List.of(
            named(new ClaimType(), orgId, "AMC", 1),
            named(new ClaimType(), orgId, "Warranty", 2),
            named(new ClaimType(), orgId, "Goodwill", 3)));

        // A new org starts on the role-mapped list only — the pre-V26 statuses exist just to
        // keep older orgs' job cards readable, so there is nothing to carry forward here.
        provisionReceiveStatuses(orgId);

        settlementModeRepo.saveAll(List.of(
            settlementMode(orgId, "Cash", false, false, true, 1),
            settlementMode(orgId, "QR / UPI", false, true, false, 2),
            settlementMode(orgId, "Bank", true, true, false, 3),
            settlementMode(orgId, "Card", false, false, false, 4),
            settlementMode(orgId, "Adv-QR", false, true, false, 5),
            settlementMode(orgId, "Adv-Cash", false, false, true, 6),
            settlementMode(orgId, "Credit (Due)", false, false, false, 7)));

        Map<String, Long> categoryId = new HashMap<>();
        for (ExpenseCategory c : expenseCategoryRepo.saveAll(List.of(
                named(new ExpenseCategory(), orgId, "Service", 1),
                named(new ExpenseCategory(), orgId, "Sales", 2),
                named(new ExpenseCategory(), orgId, "Showroom", 3),
                named(new ExpenseCategory(), orgId, "Finance", 4)))) {
            categoryId.put(c.getName(), c.getId());
        }

        Long service = categoryId.get("Service");
        Long sales = categoryId.get("Sales");
        Long showroom = categoryId.get("Showroom");
        expenseSubCategoryRepo.saveAll(List.of(
            subCategory(orgId, service, "Food (BD)", 500, 1),
            subCategory(orgId, service, "Spare Transport", 1000, 2),
            subCategory(orgId, service, "Taxi (JC)", 2000, 3),
            subCategory(orgId, service, "Fuel (JC)", 1000, 4),
            subCategory(orgId, service, "Local Purchase (JC)", 2000, 5),
            subCategory(orgId, service, "Courier Charges", 500, 6),
            subCategory(orgId, showroom, "Stationary", 1500, 1),
            subCategory(orgId, showroom, "Misc. Office", 2000, 2),
            subCategory(orgId, showroom, "Site Repair", 5000, 3),
            subCategory(orgId, showroom, "Daily Wages", 5000, 4),
            subCategory(orgId, sales, "Sales Promotion", 5000, 1),
            subCategory(orgId, sales, "RTO Expenses", 3000, 2),
            subCategory(orgId, sales, "Misc. Sales", 2000, 3)));

        expenseModeRepo.saveAll(List.of(
            expenseMode(orgId, "Cash", false, false, true, 1),
            expenseMode(orgId, "QR / UPI", false, true, false, 2),
            expenseMode(orgId, "Bank", true, true, false, 3)));

        expenseBusinessStatusRepo.saveAll(List.of(
            expenseBusinessStatus(orgId, "Open", false, 1),
            expenseBusinessStatus(orgId, "In Progress", false, 2),
            expenseBusinessStatus(orgId, "Awaiting Receipt", false, 3),
            expenseBusinessStatus(orgId, "Received Receipt", false, 4),
            expenseBusinessStatus(orgId, "Closed", false, 5),
            expenseBusinessStatus(orgId, "Transfer to Claim", true, 6),
            requiresFmApprovalStatus(orgId, "Requires Finance Approval", 7)));

        bankRepo.saveAll(List.of(
            named(new Bank(), orgId, "State Bank of India", 1),
            named(new Bank(), orgId, "HDFC Bank", 2),
            named(new Bank(), orgId, "ICICI Bank", 3),
            named(new Bank(), orgId, "Axis Bank", 4),
            named(new Bank(), orgId, "Bank of Baroda", 5),
            named(new Bank(), orgId, "Punjab National Bank", 6)));

        log.info("Provisioned default masters for orgId={}", orgId);
    }

    // --- builders ---

    private static <T extends com.dams.common.entity.OrgMaster> T named(T m, Long orgId, String name, int sort) {
        m.setOrgId(orgId);
        m.setName(name);
        m.setSortOrder(sort);
        return m;
    }

    private static ReceiveCategory receiveCategory(Long orgId, String name, int sort) {
        return named(new ReceiveCategory(), orgId, name, sort);
    }

    private static SettlementMode settlementMode(Long orgId, String name,
                                                 boolean requiresBank, boolean requiresRef, boolean cash, int sort) {
        SettlementMode m = named(new SettlementMode(), orgId, name, sort);
        m.setRequiresBank(requiresBank);
        m.setRequiresRef(requiresRef);
        m.setCash(cash);
        return m;
    }

    private static ExpenseMode expenseMode(Long orgId, String name,
                                           boolean requiresBank, boolean requiresRef, boolean cash, int sort) {
        ExpenseMode m = named(new ExpenseMode(), orgId, name, sort);
        m.setRequiresBank(requiresBank);
        m.setRequiresRef(requiresRef);
        m.setCash(cash);
        return m;
    }

    /**
     * The seven job-card statuses and who may set each: the Cashier works the first three,
     * the Accountant adds Transfer to Claim, Finance owns the claim-settlement end. Mirrors
     * V26 — change both together, or a new org and an existing one drift apart.
     */
    private void provisionReceiveStatuses(Long orgId) {
        grantStatus(orgId, "Received", 1, Role.CASHIER, Role.ACCOUNTANT, Role.FINANCE_MANAGER);
        grantStatus(orgId, "Waiting for Claim", 2, Role.CASHIER, Role.ACCOUNTANT, Role.FINANCE_MANAGER);
        grantStatus(orgId, "Credit", 3, Role.CASHIER, Role.ACCOUNTANT, Role.FINANCE_MANAGER);
        grantStatus(orgId, "Transfer to Claim", 4, Role.ACCOUNTANT, Role.FINANCE_MANAGER);
        grantStatus(orgId, "Closed", 5, Role.FINANCE_MANAGER);
        grantStatus(orgId, "Claim Received", 6, Role.FINANCE_MANAGER);
        grantStatus(orgId, "Claim Pending", 7, Role.FINANCE_MANAGER);
    }

    private void grantStatus(Long orgId, String name, int sort, Role... roles) {
        ReceiveBusinessStatus status =
            receiveBusinessStatusRepo.save(named(new ReceiveBusinessStatus(), orgId, name, sort));
        for (Role role : roles) {
            ReceiveBusinessStatusRole grant = new ReceiveBusinessStatusRole();
            grant.setOrgId(orgId);
            grant.setStatusId(status.getId());
            grant.setRole(role);
            receiveBusinessStatusRoleRepo.save(grant);
        }
    }

    private static ExpenseBusinessStatus expenseBusinessStatus(Long orgId, String name, boolean triggersClaim, int sort) {
        ExpenseBusinessStatus m = named(new ExpenseBusinessStatus(), orgId, name, sort);
        m.setTriggersClaim(triggersClaim);
        return m;
    }

    /** rev 54 — a status that sends the expense through FM pre-approval before submit. */
    private static ExpenseBusinessStatus requiresFmApprovalStatus(Long orgId, String name, int sort) {
        ExpenseBusinessStatus m = expenseBusinessStatus(orgId, name, false, sort);
        m.setRequiresFmApproval(true);
        return m;
    }

    private static ExpenseSubCategory subCategory(Long orgId, Long categoryId, String name, long limit, int sort) {
        ExpenseSubCategory m = named(new ExpenseSubCategory(), orgId, name, sort);
        m.setExpenseCategoryId(categoryId);
        m.setLimitAmount(BigDecimal.valueOf(limit));
        return m;
    }
}
